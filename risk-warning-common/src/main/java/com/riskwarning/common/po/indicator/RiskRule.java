package com.riskwarning.common.po.indicator;

import lombok.Builder;
import lombok.Data;

/**
 * 风险规则类
 */
@Builder
@Data
public class RiskRule {
    private String description;
    private StaticThreshold staticThreshold;
    private AdjustmentFactor adjustmentFactor;

    // 无参构造函数
    public RiskRule() {}

    // 全参构造函数
    public RiskRule(String description, StaticThreshold staticThreshold, AdjustmentFactor adjustmentFactor) {
        this.description = description;
        this.staticThreshold = staticThreshold;
        this.adjustmentFactor = adjustmentFactor;
    }

    @Override
    public String toString() {
        return "RiskRule{" +
                "description='" + description + '\'' +
                ", staticThreshold=" + staticThreshold +
                ", adjustmentFactor=" + adjustmentFactor +
                '}';
    }
}
