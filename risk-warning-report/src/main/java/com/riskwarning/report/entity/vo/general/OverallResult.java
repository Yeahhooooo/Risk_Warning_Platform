package com.riskwarning.report.entity.vo.general;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OverallResult {
    private Double overallScore;
    private Integer overallRiskLevel;
    private String status;
}
