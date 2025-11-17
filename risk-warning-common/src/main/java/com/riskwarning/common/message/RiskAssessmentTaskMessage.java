package com.riskwarning.common.message;

import com.riskwarning.common.dto.IndicatorResultDTO;
import com.riskwarning.common.enums.KafkaTopic;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class RiskAssessmentTaskMessage extends Message {
    private IndicatorResultDTO indicatorResult;

    public RiskAssessmentTaskMessage(String messageId, String timestamp, String traceId, String userId, String projectId, String enterpriseId, String assessmentId) {
        super(messageId, timestamp, traceId, userId, projectId, enterpriseId, assessmentId);
        this.setTopic(KafkaTopic.RISK_ASSESSMENT_TASKS);
    }

    public RiskAssessmentTaskMessage() {
        this.setTopic(KafkaTopic.RISK_ASSESSMENT_TASKS);
    }
}
