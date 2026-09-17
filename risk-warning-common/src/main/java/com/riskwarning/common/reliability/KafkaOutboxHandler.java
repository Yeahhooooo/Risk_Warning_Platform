package com.riskwarning.common.reliability;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.riskwarning.common.message.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@ConditionalOnProperty(name="assessment.reliability.enabled", havingValue="true")
public class KafkaOutboxHandler implements DurableWorkHandler {
    private final KafkaTemplate<String, Message> kafka;
    public KafkaOutboxHandler(KafkaTemplate<String, Message> kafka) { this.kafka = kafka; }
    public String kind() { return "KAFKA_OUTBOX"; }
    public int maxAttempts() { return 0; }
    public void execute(String payload) throws Exception {
        JSONObject envelope = JSON.parseObject(payload);
        Class<? extends Message> type;
        switch(envelope.getString("messageClass")) {
            case "BehaviorProcessingTaskMessage": type = BehaviorProcessingTaskMessage.class; break;
            case "IndicatorCalculationTaskMessage": type = IndicatorCalculationTaskMessage.class; break;
            case "AssessmentCompletedEventMessage": type = AssessmentCompletedEventMessage.class; break;
            case "NotificationMessage": type = NotificationMessage.class; break;
            case "QuestionnaireReminderTaskMessage": type = QuestionnaireReminderTaskMessage.class; break;
            case "RiskAssessmentTaskMessage": type = RiskAssessmentTaskMessage.class; break;
            case "Message": type = Message.class; break;
            default: throw new IllegalArgumentException("Unsupported outbox message class");
        }
        Message message = envelope.getObject("message", type);
        SendResult<String, Message> result = kafka.send(message.getTopic().getTopicName(),
                String.valueOf(message.getAssessmentId()), message).get(30, TimeUnit.SECONDS);
        log.info("[AssessmentFlow] stage=KAFKA_SEND status=ACK topic={} assessmentId={} messageId={} partition={} offset={}",
                message.getTopic(), message.getAssessmentId(), message.getMessageId(),
                result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
    }
}
