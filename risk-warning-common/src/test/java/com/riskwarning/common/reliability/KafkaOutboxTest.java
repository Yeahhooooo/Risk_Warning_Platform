package com.riskwarning.common.reliability;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.riskwarning.common.enums.DataSourceTypeEnum;
import com.riskwarning.common.message.*;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.SettableListenableFuture;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KafkaOutboxTest {
    @Test @SuppressWarnings("unchecked") void preservesMessageSubtypeAndRejectsFailedBrokerAcknowledgement() {
        KafkaTemplate<String,Message> kafka = mock(KafkaTemplate.class);
        SettableListenableFuture<SendResult<String,Message>> future = new SettableListenableFuture<>();
        future.setException(new IllegalStateException("broker unavailable"));
        when(kafka.send(eq("behavior_processing_tasks"), eq("339"), any(Message.class))).thenAnswer(call -> {
            Message sent = call.getArgument(2);
            assertInstanceOf(BehaviorProcessingTaskMessage.class, sent);
            assertEquals(Collections.singletonList("input.txt"), ((BehaviorProcessingTaskMessage)sent).getFilePaths());
            assertEquals("original-event-id", sent.getMessageId());
            return future;
        });
        Message message = new BehaviorProcessingTaskMessage("original-event-id", "now", "trace", 1L, 257L, 339L,
                DataSourceTypeEnum.FILE_UPLOAD, Collections.singletonList("input.txt"));
        JSONObject envelope = new JSONObject(); envelope.put("messageClass", "BehaviorProcessingTaskMessage"); envelope.put("message", message);
        assertThrows(ExecutionException.class, () -> new KafkaOutboxHandler(kafka).execute(JSON.toJSONString(envelope)));
    }
}
