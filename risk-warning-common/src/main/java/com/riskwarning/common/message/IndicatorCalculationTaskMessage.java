package com.riskwarning.common.message;

import com.riskwarning.common.enums.KafkaTopic;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 指标计算任务消息
 * 用于解耦行为处理和指标计算
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class IndicatorCalculationTaskMessage extends Message {

    public IndicatorCalculationTaskMessage() {
        this.setTopic(KafkaTopic.INDICATOR_CALCULATION_TASKS);
    }

    public IndicatorCalculationTaskMessage(String messageId, String timestamp, String traceId,
                                          Long userId, Long projectId, Long assessmentId) {
        super(messageId, timestamp, traceId, userId, projectId, assessmentId);
        this.setTopic(KafkaTopic.INDICATOR_CALCULATION_TASKS);
    }
}

