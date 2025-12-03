package com.riskwarning.common.po.indicator;

import com.riskwarning.common.enums.RuleTypeEnum;
import lombok.Builder;
import lombok.Data;

/**
 * 计算规则类
 */
@Builder
@Data
public class CalculationRule {
    private RuleTypeEnum ruleType;
    private BinaryRule binaryRule;
    private RangeRule rangeRule;

    // 无参构造函数
    public CalculationRule() {}

    // 全参构造函数
    public CalculationRule(RuleTypeEnum ruleType, BinaryRule binaryRule, RangeRule rangeRule) {
        this.ruleType = ruleType;
        this.binaryRule = binaryRule;
        this.rangeRule = rangeRule;
    }

    @Override
    public String toString() {
        return "CalculationRule{" +
                "ruleType='" + ruleType + '\'' +
                ", binaryRule=" + binaryRule +
                ", rangeRule=" + rangeRule +
                '}';
    }
}
