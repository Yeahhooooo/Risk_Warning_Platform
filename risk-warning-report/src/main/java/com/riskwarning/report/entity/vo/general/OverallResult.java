package com.riskwarning.report.entity.vo.general;

import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.common.enums.risk.RiskLevelEnum;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OverallResult {
    private Double overallScore;
    private RiskLevelEnum overallRiskLevel;
    private AssessmentStatusEnum status;
    private String failureCode;
    private String failureReason;
    private String failureStage;
    private java.time.LocalDateTime failedAt;
}
