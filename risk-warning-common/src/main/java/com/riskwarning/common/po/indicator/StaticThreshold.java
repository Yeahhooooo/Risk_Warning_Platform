package com.riskwarning.common.po.indicator;

import com.riskwarning.common.enums.RiskLevelEnum;
import lombok.Builder;
import lombok.Data;

/**
 * 静态阈值类
 */
@Builder
@Data
public class StaticThreshold {
    private String operator;
    private Double thresholdValue;
    private RiskLevelEnum riskLevel;

    // 无参构造函数
    public StaticThreshold() {}

    // 全参构造函数
    public StaticThreshold(String operator, Double thresholdValue, RiskLevelEnum riskLevel) {
        this.operator = operator;
        this.thresholdValue = thresholdValue;
        this.riskLevel = riskLevel;
    }

    @Override
    public String toString() {
        return "StaticThreshold{" +
                "operator='" + operator + '\'' +
                ", thresholdValue=" + thresholdValue +
                ", riskLevel='" + riskLevel + '\'' +
                '}';
    }
}
