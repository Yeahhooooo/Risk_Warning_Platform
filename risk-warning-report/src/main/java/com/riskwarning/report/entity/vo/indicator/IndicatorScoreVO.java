package com.riskwarning.report.entity.vo.indicator;

import com.riskwarning.common.enums.risk.RiskLevelEnum;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class IndicatorScoreVO {
    private String indicatorId;
    private String indicatorName;
    private String dimension;
    /**
     * 该指标在知识库中的完整路径：维度 &gt; 二级指标 &gt; 三级指标 &gt; 三级指标项。
     *
     * <p>写死的演示数据集恒有值；真实评估的指标没有该元数据，返回 {@code null}。</p>
     */
    private String relatedIndicator;
    /** Descriptions only; no behavior identifiers, metadata or regulations are exposed here. */
    private List<String> relatedBehaviors;
    /** 0-100; null means the source score cannot be evaluated. */
    private Double score;
    private Double maxScore;
    private RiskLevelEnum riskLevel;
    private Boolean riskTriggered;
}
