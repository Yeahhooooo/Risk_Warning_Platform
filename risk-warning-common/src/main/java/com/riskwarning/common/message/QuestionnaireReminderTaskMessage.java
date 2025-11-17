package com.riskwarning.common.message;

import com.riskwarning.common.enums.KafkaTopic;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class QuestionnaireReminderTaskMessage extends Message {

    public QuestionnaireReminderTaskMessage(String messageId, String timestamp, String traceId, String userId, String projectId, String enterpriseId, String assessmentId) {
        super(messageId, timestamp, traceId, userId, projectId, enterpriseId, assessmentId);
        this.setTopic(KafkaTopic.QUESTIONNAIRE_REMINDER_TASKS);
    }

    public QuestionnaireReminderTaskMessage() {
        this.setTopic(KafkaTopic.QUESTIONNAIRE_REMINDER_TASKS);
    }
}
