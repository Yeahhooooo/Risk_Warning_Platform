package com.riskwarning.common.message;

import com.riskwarning.common.enums.KafkaTopic;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class QuestionnaireReminderTaskMessage extends Message {

    public QuestionnaireReminderTaskMessage(String messageId, String timestamp, String traceId, Long userId, Long projectId, Long assessmentId) {
        super(messageId, timestamp, traceId, userId, projectId, assessmentId);
        this.setTopic(KafkaTopic.QUESTIONNAIRE_REMINDER_TASKS);
    }

    public QuestionnaireReminderTaskMessage() {
        this.setTopic(KafkaTopic.QUESTIONNAIRE_REMINDER_TASKS);
    }
}
