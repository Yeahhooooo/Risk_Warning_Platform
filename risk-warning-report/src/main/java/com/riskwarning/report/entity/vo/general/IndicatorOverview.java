package com.riskwarning.report.entity.vo.general;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IndicatorOverview {
    private Integer behaviorIndicators;
    private Integer questionnaireIndicators;
    private Integer riskTriggeredIndicators;
    private Integer safeIndicators;
}
