package com.riskwarning.common.po.indicator;

import com.riskwarning.common.enums.RiskLevelEnum;
import lombok.Builder;
import lombok.Data;

/**
 * 调整因子类
 */
@Builder
@Data
//@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AdjustmentFactor {
    private Boolean enabled;
    private Integer requiredLowCount;
    private RiskLevelEnum riskLevelIncrease;

    // 无参构造函数
    public AdjustmentFactor() {}

    // 全参构造函数
    public AdjustmentFactor(Boolean enabled, Integer requiredLowCount, RiskLevelEnum riskLevelIncrease) {
        this.enabled = enabled;
        this.requiredLowCount = requiredLowCount;
        this.riskLevelIncrease = riskLevelIncrease;
    }

    @Override
    public String toString() {
        return "AdjustmentFactor{" +
                "enabled=" + enabled +
                ", requiredLowCount=" + requiredLowCount +
                ", riskLevelIncrease='" + riskLevelIncrease + '\'' +
                '}';
    }
}
