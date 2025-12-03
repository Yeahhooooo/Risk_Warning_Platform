package com.riskwarning.common.po.indicator;

import lombok.Builder;
import lombok.Data;

/**
 * 范围规则类
 */
@Data
@Builder
public class RangeRule {
    private Double minValue;
    private Double maxValue;
    private String calculationMethod;
    private Double maxScore;         // calculation_rule中使用
    private Double outOfMinScore;    // default_calculation_rule中使用
    private Double outOfMaxScore;    // default_calculation_rule中使用

    // 无参构造函数
    public RangeRule() {}

    // 全参构造函数
    public RangeRule(Double minValue, Double maxValue, String calculationMethod,
                     Double maxScore, Double outOfMinScore, Double outOfMaxScore) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.calculationMethod = calculationMethod;
        this.maxScore = maxScore;
        this.outOfMinScore = outOfMinScore;
        this.outOfMaxScore = outOfMaxScore;
    }

    @Override
    public String toString() {
        return "RangeRule{" +
                "minValue=" + minValue +
                ", maxValue=" + maxValue +
                ", calculationMethod='" + calculationMethod + '\'' +
                ", maxScore=" + maxScore +
                ", outOfMinScore=" + outOfMinScore +
                ", outOfMaxScore=" + outOfMaxScore +
                '}';
    }
}
