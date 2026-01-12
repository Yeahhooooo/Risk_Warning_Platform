package com.riskwarning.report.entity.vo.indicator;


import com.riskwarning.common.enums.RiskDimensionEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndicatorDistributionVO {

    private RiskDimensionEnum riskDimensionEnum;// 为空表示总体维度

    private Long assessmentId;

    private Double totalScore;

    private Integer totalCount;

    private Integer riskTriggeredCount;

    private Integer safeCount;

    private LocalDateTime assessmentTime;

    private List<ScoreRatioDistributionItemVO> scoreDistributions;

    private Map<RiskDimensionEnum, IndicatorDistributionVO> dimensionDistributions;

}
