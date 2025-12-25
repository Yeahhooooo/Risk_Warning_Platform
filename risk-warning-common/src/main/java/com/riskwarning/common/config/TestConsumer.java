package com.riskwarning.common.config;

import com.riskwarning.common.message.Message;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;

@Component
public class TestConsumer {
    private CountDownLatch latch = new CountDownLatch(1);

    public Message receiveMessage = null;

    public void reset() {
        latch = new CountDownLatch(1);
        receiveMessage = null;
    }

    @KafkaListener(topics = "test", groupId = "test-group")
    public void listen(Message message) {
        this.receiveMessage = message;
        latch.countDown();
    }

    public Message awaitMessage() throws InterruptedException {
        latch.await();
        return receiveMessage;
    }
}
