package com.riskwarning.report.entity.vo.indicator;

import com.riskwarning.common.enums.risk.RiskLevelEnum;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IndicatorScoreVO {
    private String indicatorId;
    private String indicatorName;
    private String dimension;
    /** 0-100; null means the source score cannot be evaluated. */
    private Double score;
    private Double maxScore;
    private RiskLevelEnum riskLevel;
    private Boolean riskTriggered;
}
