package com.riskwarning.common.message;

import com.riskwarning.common.dto.IndicatorResultDTO;
import com.riskwarning.common.enums.KafkaTopic;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class RiskAssessmentTaskMessage extends Message {
    private IndicatorResultDTO indicatorResult;

    public RiskAssessmentTaskMessage(String messageId, String timestamp, String traceId, Long userId, Long projectId, Long assessmentId) {
        super(messageId, timestamp, traceId, userId, projectId, assessmentId);
        this.setTopic(KafkaTopic.RISK_ASSESSMENT_TASKS);
    }

    public RiskAssessmentTaskMessage() {
        this.setTopic(KafkaTopic.RISK_ASSESSMENT_TASKS);
    }

    public static void main(String[] args) {

    }
}
