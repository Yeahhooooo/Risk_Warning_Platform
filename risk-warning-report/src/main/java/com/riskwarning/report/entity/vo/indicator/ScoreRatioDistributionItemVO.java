package com.riskwarning.report.entity.vo.indicator;

import com.riskwarning.common.enums.RiskDimensionEnum;
import lombok.Data;

@Data
public class ScoreRatioDistributionItemVO {

    private Double startScoreRatio;

    private Double endScoreRatio;

    // 该区间在总区间数目占比
    private Double ratio;

    private Double totalScore;

    private Integer totalCount;

    private Integer riskTriggeredCount;

    private Integer safeCount;


}
