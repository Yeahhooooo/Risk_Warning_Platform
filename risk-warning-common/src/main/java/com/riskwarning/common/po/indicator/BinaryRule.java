package com.riskwarning.common.po.indicator;

import lombok.Builder;
import lombok.Data;

/**
 * 二元规则类
 */
@Builder
@Data
public class BinaryRule {
    private String condition;
    private Double trueScore;
    private Double falseScore;

    // 无参构造函数
    public BinaryRule() {}

    // 全参构造函数
    public BinaryRule(String condition, Double trueScore, Double falseScore) {
        this.condition = condition;
        this.trueScore = trueScore;
        this.falseScore = falseScore;
    }

    @Override
    public String toString() {
        return "BinaryRule{" +
                "condition='" + condition + '\'' +
                ", trueScore=" + trueScore +
                ", falseScore=" + falseScore +
                '}';
    }
}
