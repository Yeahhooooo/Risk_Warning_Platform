package com.riskwarning.report.entity.vo.general;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndicatorOverview {
    private Integer behaviorIndicators;
    private Integer questionnaireIndicators;
    private Integer riskTriggeredIndicators;
    private Integer safeIndicators;
}
