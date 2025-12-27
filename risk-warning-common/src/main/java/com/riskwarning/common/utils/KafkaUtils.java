package com.riskwarning.common.utils;

import com.riskwarning.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaUtils {

    @Autowired
    private KafkaTemplate<String, Message> kafkaTemplate;

    public  void sendMessage(Message message) {
        kafkaTemplate.send(message.getTopic().getTopicName(), message);
    }
}
