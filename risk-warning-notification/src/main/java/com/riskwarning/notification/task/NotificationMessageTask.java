package com.riskwarning.notification.task;

import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.message.NotificationMessage;
import com.riskwarning.notification.websocket.WebSocketChannelManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 通知消息Kafka消费者
 * 负责监听通知消息并通过WebSocket推送给前端
 */
@Component
@Slf4j
public class NotificationMessageTask {

    @Autowired
    private WebSocketChannelManager channelManager;

    /**
     * 监听通知任务消息
     */
    @KafkaListener(topics = "notification_tasks", groupId = "notification-consumer")
    public void onNotificationMessage(NotificationMessage message) {
        log.info("接收到通知消息: messageId={}, userId={}, type={}",
                message.getMessageId(), message.getUserId(), message.getNotificationType());

        try {
            // 构建推送给前端的消息
            WebSocketNotification notification = new WebSocketNotification();
            notification.setType("notification");
            notification.setNotificationType(message.getNotificationType().name());
            notification.setTitle(message.getTitle());
            notification.setContent(message.getContent());
            notification.setProjectId(message.getProjectId());
            notification.setAssessmentId(message.getAssessmentId());
            notification.setTimestamp(message.getTimestamp());
            notification.setExtraData(message.getExtraData());

            String jsonMessage = JSON.toJSONString(notification);

            // 向指定用户推送消息
            Long userId = message.getUserId();
            if (userId != null) {
                boolean sent = channelManager.sendMessageToUser(userId, jsonMessage);
                if (sent) {
                    log.info("通知消息已推送给用户: userId={}", userId);
                } else {
                    log.warn("用户不在线，通知消息未能推送: userId={}", userId);
                    // 可以在这里实现离线消息存储逻辑
                    handleOfflineMessage(userId, message);
                }
            } else {
                // 如��没有指定用户ID，可以考虑广播或其他处理逻辑
                log.warn("通知消息未指定用户ID，忽略消息: messageId={}", message.getMessageId());
            }
        } catch (Exception e) {
            log.error("处理通知消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理离线消息（可以存储到数据库，等用户上线后再推送）
     */
    private void handleOfflineMessage(Long userId, NotificationMessage message) {
        // TODO: 实现离线消息存储逻辑
        // 可以存储到Redis或数据库中，等用户上线后再推送
        log.info("存储离线消息: userId={}, messageId={}", userId, message.getMessageId());
    }

    /**
     * WebSocket通知消息结构
     */
    @lombok.Data
    private static class WebSocketNotification {
        /**
         * 消息类型，固定为 "notification"
         */
        private String type;
        /**
         * 通知类型
         */
        private String notificationType;
        /**
         * 通知标题
         */
        private String title;
        /**
         * 通知内容
         */
        private String content;
        /**
         * 项目ID
         */
        private Long projectId;
        /**
         * 评估ID
         */
        private Long assessmentId;
        /**
         * 时间戳
         */
        private String timestamp;
        /**
         * 额外数据
         */
        private String extraData;
    }
}

