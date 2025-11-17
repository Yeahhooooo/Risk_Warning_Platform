package com.riskwarning.common.utils;

import com.riskwarning.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;

@Component
public class KafkaUtils {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TestConsumer testConsumer;


    public void sendMessage(Message message) {
        String topic = message.getTopic().getTopicName();
        String payload = message.toJson();
        kafkaTemplate.send(topic, payload);
    }

    public String receiveMessage() throws InterruptedException {
        // 这里可以添加接收消息的逻辑，正常情况应该为listener监听，此方法仅作示例
        return testConsumer.awaitMessage();
    }


}

@Component
class TestConsumer {
    private CountDownLatch latch = new CountDownLatch(1);

    public String receiveMessage = null;

    public void reset() {
        latch = new CountDownLatch(1);
        receiveMessage = null;
    }

    @KafkaListener(topics = "test", groupId = "test-group")
    public void listen(String message) {
        this.receiveMessage = message;
        latch.countDown();
    }

    public String awaitMessage() throws InterruptedException {
        latch.await();
        return receiveMessage;
    }
}
