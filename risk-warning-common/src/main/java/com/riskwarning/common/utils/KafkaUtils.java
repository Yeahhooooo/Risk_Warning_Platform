package com.riskwarning.common.utils;

import com.riskwarning.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Stack;

@Component
public class KafkaUtils {

    @Autowired
    private KafkaTemplate<String, Message> kafkaTemplate;

    public  void sendMessage(Message message) {
        kafkaTemplate.send(message.getTopic().getTopicName(), message);
    }

    public static void main(String[] args) {
        HashSet<String> hashSet = new HashSet<>();
        hashSet.add("1");
        hashSet.add("2");
        Stack[] stacks = new Stack[2];

    }
}
