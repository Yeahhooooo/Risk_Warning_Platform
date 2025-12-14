package com.riskwarning.common.message;

import com.riskwarning.common.enums.KafkaTopic;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class IndicatorCalculationTaskMessage extends Message{

    public IndicatorCalculationTaskMessage(String messageId, String timestamp, String traceId, Long userId, Long projectId, Long enterpriseId, Long assessmentId) {
        super(messageId, timestamp, traceId, userId, projectId, enterpriseId, assessmentId);
        this.setTopic(KafkaTopic.INDICATOR_CALCULATION_TASKS);
    }

    public IndicatorCalculationTaskMessage() {
        this.setTopic(KafkaTopic.INDICATOR_CALCULATION_TASKS);
    }

}
