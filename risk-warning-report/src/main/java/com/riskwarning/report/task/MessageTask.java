package com.riskwarning.report.task;


import com.riskwarning.common.message.AssessmentCompletedEventMessage;
import com.riskwarning.common.utils.KafkaUtils;
import com.riskwarning.report.service.AssessmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class MessageTask implements com.riskwarning.common.reliability.DurableWorkHandler {

    @Autowired
    private com.riskwarning.common.reliability.DurableWorkStore store;

    public String kind() { return "REPORT_INBOX"; }

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private KafkaUtils kafkaUtils;

    @KafkaListener(topics = "assessment_completed_events", groupId = "test-consumer", containerFactory="durableKafkaListenerContainerFactory")
    public void onMessage(AssessmentCompletedEventMessage message) {
        if(message == null || message.getMessageId() == null || message.getAssessmentId() == null)
            throw new IllegalArgumentException("Assessment event requires messageId and assessmentId");
        store.enqueue(kind(), message.getMessageId(), com.alibaba.fastjson2.JSON.toJSONString(message));
        log.info("[AssessmentFlow] stage=REPORT_TASK status=PERSISTED assessmentId={} messageId={}", message.getAssessmentId(), message.getMessageId());
    }

    public void execute(String payload) {
        AssessmentCompletedEventMessage message = com.alibaba.fastjson2.JSON.parseObject(payload, AssessmentCompletedEventMessage.class);
        log.info("接收评估结束任务，开始汇总信息");
        long started = System.nanoTime();
        log.info("[AssessmentFlow] stage=REPORT_AGGREGATE status=START projectId={} assessmentId={} messageId={} traceId={}",
                message.getProjectId(), message.getAssessmentId(), message.getMessageId(), message.getTraceId());
        try {
            assessmentService.aggregateInformation(message.getUserId(), message.getProjectId(), message.getAssessmentId());
            log.info("[AssessmentFlow] stage=REPORT_AGGREGATE status=DONE projectId={} assessmentId={} elapsedMs={}",
                    message.getProjectId(), message.getAssessmentId(), (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException failure) {
            log.error("[AssessmentFlow] stage=REPORT_AGGREGATE status=FAILED projectId={} assessmentId={} elapsedMs={}",
                    message.getProjectId(), message.getAssessmentId(), (System.nanoTime() - started) / 1_000_000, failure);
            throw failure;
        }
    }
}
