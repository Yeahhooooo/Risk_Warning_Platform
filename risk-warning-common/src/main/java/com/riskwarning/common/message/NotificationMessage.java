package com.riskwarning.common.message;

import com.riskwarning.common.enums.KafkaTopic;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class NotificationMessage extends Message {

    /**
     * 通知类型
     */
    private NotificationType notificationType;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 额外数据（可选，JSON格式）
     */
    private String extraData;

    public NotificationMessage() {
        this.setTopic(KafkaTopic.NOTIFICATION_TASKS);
    }

    public NotificationMessage(String messageId, String timestamp, String traceId, Long userId, Long projectId, Long assessmentId) {
        super(messageId, timestamp, traceId, userId, projectId, assessmentId);
        this.setTopic(KafkaTopic.NOTIFICATION_TASKS);
    }

    public NotificationMessage(String messageId, String timestamp, String traceId, Long userId, Long projectId, Long assessmentId,
                               NotificationType notificationType, String title, String content) {
        super(messageId, timestamp, traceId, userId, projectId, assessmentId);
        this.setTopic(KafkaTopic.NOTIFICATION_TASKS);
        this.notificationType = notificationType;
        this.title = title;
        this.content = content;
    }

    /**
     * 通知类型枚举
     */
    public enum NotificationType {
        /**
         * 评估完成通知
         */
        ASSESSMENT_COMPLETED,
        /**
         * 风险预警通知
         */
        RISK_WARNING,
        /**
         * 系统通知
         */
        SYSTEM_NOTIFICATION,
        /**
         * 问卷提醒
         */
        QUESTIONNAIRE_REMINDER
    }
}

