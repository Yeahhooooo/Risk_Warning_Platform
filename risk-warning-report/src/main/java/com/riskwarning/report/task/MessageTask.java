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
public class MessageTask {

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private KafkaUtils kafkaUtils;

    @KafkaListener(topics = "assessment_completed_events", groupId = "test-consumer")
    public void onMessage(AssessmentCompletedEventMessage message) {
        log.info("接收评估结束任务，开始汇总信息");
        assessmentService.aggregateInformation(message.getUserId(), message.getProjectId(), message.getAssessmentId());
    }
}
