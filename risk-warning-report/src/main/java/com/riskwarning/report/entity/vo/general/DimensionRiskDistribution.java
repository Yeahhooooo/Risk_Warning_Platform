package com.riskwarning.report.entity.vo.general;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DimensionRiskDistribution {
    private String dimension;
    private Integer riskCount;
    private Integer lowRiskCount;
    private Integer mediumRiskCount;
    private Integer highRiskCount;
}
