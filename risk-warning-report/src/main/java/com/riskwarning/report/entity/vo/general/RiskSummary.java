package com.riskwarning.report.entity.vo.general;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiskSummary {
    private Integer totalRisks;
    private Integer highRiskCount;
    private Integer mediumRiskCount;
    private Integer lowRiskCount;
}
