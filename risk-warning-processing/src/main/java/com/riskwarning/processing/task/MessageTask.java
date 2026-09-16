package com.riskwarning.processing.task;

import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.enums.DataSourceTypeEnum;
import com.riskwarning.common.message.*;
import com.riskwarning.common.reliability.*;
import com.riskwarning.common.utils.KafkaUtils;
import com.riskwarning.processing.batch.BatchJob;
import com.riskwarning.processing.service.BehaviorProcessingService;
import com.riskwarning.processing.service.DocumentProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class MessageTask {
    private final DurableWorkStore store;
    private final BatchJob batch;
    private final DocumentProcessingService documents;
    private final BehaviorProcessingService behaviors;
    private final KafkaUtils kafka;
    public MessageTask(DurableWorkStore store, BatchJob batch, DocumentProcessingService documents,
            BehaviorProcessingService behaviors, KafkaUtils kafka) {
        this.store=store; this.batch=batch; this.documents=documents; this.behaviors=behaviors; this.kafka=kafka;
    }

    @KafkaListener(topics="behavior_processing_tasks", groupId="test-consumer", containerFactory="durableKafkaListenerContainerFactory")
    public void onMessage(BehaviorProcessingTaskMessage message) {
        validate(message);
        store.enqueue("BEHAVIOR_INBOX", message.getMessageId(), JSON.toJSONString(message));
        log.info("[AssessmentFlow] stage=BEHAVIOR_TASK status=PERSISTED projectId={} assessmentId={} messageId={}",
                message.getProjectId(), message.getAssessmentId(), message.getMessageId());
    }

    @KafkaListener(topics="indicator_calculation_tasks", groupId="indicator-calculation-consumer", containerFactory="durableKafkaListenerContainerFactory")
    public void onMessage(IndicatorCalculationTaskMessage message) {
        validate(message);
        store.enqueue("INDICATOR_INBOX", message.getMessageId(), JSON.toJSONString(message));
        log.info("[AssessmentFlow] stage=INDICATOR_TASK status=PERSISTED projectId={} assessmentId={} messageId={}",
                message.getProjectId(), message.getAssessmentId(), message.getMessageId());
    }
    private void validate(Message message) {
        if(message == null || message.getMessageId() == null || message.getProjectId() == null || message.getAssessmentId() == null)
            throw new IllegalArgumentException("Assessment message requires messageId, projectId and assessmentId");
    }

    @Bean public DurableWorkHandler behaviorInboxHandler() {
        return new DurableWorkHandler() {
            public String kind() { return "BEHAVIOR_INBOX"; }
            public void onExhausted(String payload) {
                behaviors.markAssessmentFailed(JSON.parseObject(payload, BehaviorProcessingTaskMessage.class).getAssessmentId());
            }
            public void execute(String payload) throws Exception {
                BehaviorProcessingTaskMessage message = JSON.parseObject(payload, BehaviorProcessingTaskMessage.class);
                if(message.getType() != DataSourceTypeEnum.FILE_UPLOAD) throw new IllegalArgumentException("Unsupported task type: " + message.getType());
                log.info("[AssessmentFlow] stage=DOCUMENT_EXTRACT status=START projectId={} assessmentId={}", message.getProjectId(), message.getAssessmentId());
                List<String> inputs = documents.processDocument(message.getProjectId(), new ArrayList<>(), message.getFilePaths());
                log.info("[AssessmentFlow] stage=DOCUMENT_EXTRACT status=DONE projectId={} assessmentId={} fileCount={}", message.getProjectId(), message.getAssessmentId(), inputs.size());
                JobExecution execution = batch.runBatchJob(message.getProjectId(), inputs);
                if(!batch.isJobExecutionSuccessful(execution)) throw new IllegalStateException(batch.getJobExecutionSummary(execution));
                kafka.sendMessage(new IndicatorCalculationTaskMessage(message.getMessageId() + "-indicators",
                        String.valueOf(System.currentTimeMillis()), message.getTraceId(), message.getUserId(),
                        message.getProjectId(), message.getAssessmentId()));
                log.info("[AssessmentFlow] stage=BEHAVIOR_BATCH status=DONE projectId={} assessmentId={} inputFilesRetained=true", message.getProjectId(), message.getAssessmentId());
            }
        };
    }
    @Bean public DurableWorkHandler indicatorInboxHandler() {
        return new DurableWorkHandler() {
            public String kind() { return "INDICATOR_INBOX"; }
            public void onExhausted(String payload) {
                behaviors.markAssessmentFailed(JSON.parseObject(payload, IndicatorCalculationTaskMessage.class).getAssessmentId());
            }
            public void execute(String payload) {
                IndicatorCalculationTaskMessage message = JSON.parseObject(payload, IndicatorCalculationTaskMessage.class);
                behaviors.processProjectBehaviors(message.getUserId(), message.getProjectId(), message.getAssessmentId());
                log.info("[AssessmentFlow] stage=INDICATOR_CALCULATE status=DONE projectId={} assessmentId={}", message.getProjectId(), message.getAssessmentId());
            }
        };
    }
}
