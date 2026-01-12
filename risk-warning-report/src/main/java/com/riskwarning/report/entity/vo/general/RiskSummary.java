package com.riskwarning.report.entity.vo.general;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskSummary {
    private Integer totalRisks;
    private Integer highRiskCount;
    private Integer mediumRiskCount;
    private Integer lowRiskCount;
}
