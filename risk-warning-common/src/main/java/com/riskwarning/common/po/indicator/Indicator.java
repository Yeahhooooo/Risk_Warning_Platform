package com.riskwarning.common.po.indicator;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.List;

/**
 * t_indicator 索引的主实体类
 */

@Builder
@Data
public class Indicator {
    private String id; // es默认的id字段，不需要手动设置
    // Getter 和 Setter 方法
    @Getter
    private String name;
    private String description;
    private String type;
    private Integer indicatorLevel;
    private String parentIndicatorId;
    private String dimension;
    private List<String> industry;
    private String region;
    private List<String> tags;
    private Double maxScore;
    private CalculationRule calculationRule;
    private RiskRule riskRule;

    // 无参构造函数
    public Indicator() {}

    // 全参构造函数
    public Indicator(String id, String name, String description, String type, Integer indicatorLevel,
                     String parentIndicatorId, String dimension, List<String> industry, String region,
                     List<String> tags, Double maxScore, CalculationRule calculationRule,
                     RiskRule riskRule) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.indicatorLevel = indicatorLevel;
        this.parentIndicatorId = parentIndicatorId;
        this.dimension = dimension;
        this.industry = industry;
        this.region = region;
        this.tags = tags;
        this.maxScore = maxScore;
        this.calculationRule = calculationRule;
//        this.defaultCalculationRule = defaultCalculationRule;
        this.riskRule = riskRule;
    }


    @Override
    public String toString() {
        return "TIndicator{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", type='" + type + '\'' +
                ", indicatorLevel=" + indicatorLevel +
                ", parentIndicatorId='" + parentIndicatorId + '\'' +
                ", dimension='" + dimension + '\'' +
                ", industry=" + industry +
                ", region='" + region + '\'' +
                ", tags=" + tags +
                ", maxScore=" + maxScore +
                ", calculationRule=" + calculationRule +
                ", riskRule=" + riskRule +
                '}';
    }
}
