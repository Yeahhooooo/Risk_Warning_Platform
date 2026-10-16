package com.riskwarning.common.message;


import com.riskwarning.common.enums.KafkaTopic;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AssessmentCompletedEventMessage extends Message {

    /** Optional failure outcome. Null keeps the existing calculation-completed event contract. */
    private String failureStage;

    /** Internal diagnostic information; never copied into the frontend response. */
    private String failureDetail;

    public AssessmentCompletedEventMessage(String messageId, String timestamp, String traceId, Long userId, Long projectId, Long assessmentId) {
        super(messageId, timestamp, traceId, userId, projectId, assessmentId);
        this.setTopic(KafkaTopic.ASSESSMENT_COMPLETED_EVENTS);
    }

    public AssessmentCompletedEventMessage() {
        this.setTopic(KafkaTopic.ASSESSMENT_COMPLETED_EVENTS);
    }
}
