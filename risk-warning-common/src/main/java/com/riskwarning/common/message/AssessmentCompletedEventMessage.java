package com.riskwarning.common.message;


import com.riskwarning.common.enums.KafkaTopic;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AssessmentCompletedEventMessage extends Message {

    public AssessmentCompletedEventMessage(String messageId, String timestamp, String traceId, String userId, String projectId, String enterpriseId, String assessmentId) {
        super(messageId, timestamp, traceId, userId, projectId, enterpriseId, assessmentId);
        this.setTopic(KafkaTopic.ASSESSMENT_COMPLETED_EVENTS);
    }

    public AssessmentCompletedEventMessage() {
        this.setTopic(KafkaTopic.ASSESSMENT_COMPLETED_EVENTS);
    }
}
