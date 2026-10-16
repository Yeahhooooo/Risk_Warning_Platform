package com.riskwarning.notification.task;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.riskwarning.common.message.NotificationMessage;
import com.riskwarning.notification.websocket.WebSocketChannelManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssessmentFailureNotificationTest {
    @Test void failedNotificationReachesFrontendWithStatusAndReason() {
        WebSocketChannelManager channels = mock(WebSocketChannelManager.class);
        when(channels.sendMessageToUser(eq(8L), anyString())).thenReturn(true);
        NotificationMessageTask handler = new NotificationMessageTask();
        ReflectionTestUtils.setField(handler, "channelManager", channels);
        NotificationMessage notification = new NotificationMessage("failed-12", "now", "trace", 8L, 10L, 12L);
        notification.setNotificationType(NotificationMessage.NotificationType.ASSESSMENT_FAILED);
        notification.setContent("指标计算失败，请稍后重新评估");
        notification.setExtraData("{\"status\":\"FAILED\",\"failureReason\":\"指标计算失败，请稍后重新评估\"}");
        handler.onNotificationMessage(notification);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(channels).sendMessageToUser(eq(8L), message.capture());
        JSONObject body = JSON.parseObject(message.getValue());
        assertEquals("ASSESSMENT_FAILED", body.getString("notificationType"));
        assertEquals(12L, body.getLong("assessmentId"));
        assertEquals("FAILED", JSON.parseObject(body.getString("extraData")).getString("status"));
        assertEquals(notification.getContent(), body.getString("content"));
    }
}
