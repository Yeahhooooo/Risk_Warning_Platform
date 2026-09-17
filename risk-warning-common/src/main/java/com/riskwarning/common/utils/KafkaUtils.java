package com.riskwarning.common.utils;

import com.riskwarning.common.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Stack;

@Component
@Slf4j
public class KafkaUtils {

    @Autowired
    private KafkaTemplate<String, Message> kafkaTemplate;

    @Autowired(required = false)
    private com.riskwarning.common.reliability.DurableWorkStore durableWorkStore;

    public  void sendMessage(Message message) {
        if (durableWorkStore != null) {
            com.alibaba.fastjson2.JSONObject envelope = new com.alibaba.fastjson2.JSONObject();
            envelope.put("messageClass", message.getClass().getSimpleName());
            envelope.put("message", message);
            durableWorkStore.enqueue("KAFKA_OUTBOX", message.getMessageId(), envelope.toJSONString());
            log.info("[AssessmentFlow] stage=KAFKA_OUTBOX status=PERSISTED messageId={}", message.getMessageId());
            return;
        }
        String topic = message.getTopic().getTopicName();
        long started = System.nanoTime();
        log.info("[AssessmentFlow] stage=KAFKA_SEND status=START topic={} projectId={} assessmentId={} messageId={} traceId={}",
                topic, message.getProjectId(), message.getAssessmentId(), message.getMessageId(), message.getTraceId());
        try {
            kafkaTemplate.send(topic, message).addCallback(result -> {
                log.info("[AssessmentFlow] stage=KAFKA_SEND status=ACK topic={} projectId={} assessmentId={} messageId={} partition={} offset={} elapsedMs={}",
                        topic, message.getProjectId(), message.getAssessmentId(), message.getMessageId(),
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset(),
                        (System.nanoTime() - started) / 1_000_000);
            }, failure -> {
                log.error("[AssessmentFlow] stage=KAFKA_SEND status=FAILED topic={} projectId={} assessmentId={} messageId={} elapsedMs={}",
                        topic, message.getProjectId(), message.getAssessmentId(), message.getMessageId(),
                        (System.nanoTime() - started) / 1_000_000, failure);
            });
        } catch (RuntimeException failure) {
            log.error("[AssessmentFlow] stage=KAFKA_SEND status=FAILED topic={} projectId={} assessmentId={} messageId={}",
                    topic, message.getProjectId(), message.getAssessmentId(), message.getMessageId(), failure);
            throw failure;
        }
    }

    public static void main(String[] args) {
        HashSet<String> hashSet = new HashSet<>();
        hashSet.add("1");
        hashSet.add("2");
        Stack[] stacks = new Stack[2];

    }
}
