package com.riskwarning.common.enums.risk;

public enum RiskLevelEnum {

    LOW_RISK("LOW_RISK", "低风险"),

    MEDIUM_RISK("MEDIUM_RISK", "中风险"),

    HIGH_RISK("HIGH_RISK", "高风险");

    private final String code;

    private final String description;

    private static final Double LOW_RISK_WEIGHT = 0.2;

    private static final Double MEDIUM_RISK_WEIGHT = 0.4;

    private static final Double HIGH_RISK_WEIGHT = 1.0;

    RiskLevelEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static RiskLevelEnum fromValue(String riskLevel) {
        for (RiskLevelEnum level : RiskLevelEnum.values()) {
            if (level.name().equalsIgnoreCase(riskLevel)) {
                return level;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return this.code;
    }

    public static RiskLevelEnum getByScoreRatio(double scoreRatio) {
        if (scoreRatio >= 0.4) {
            return LOW_RISK;
        } else if (scoreRatio >= 0.2) {
            return MEDIUM_RISK;
        } else {
            return HIGH_RISK;
        }
    }

    public static RiskLevelEnum getByRiskCount(int lowRiskCount, int mediumRiskCount, int highRiskCount) {
        if(highRiskCount >= 5){
            return HIGH_RISK;
        }
        if(mediumRiskCount >= 5){
            return MEDIUM_RISK;
        }
        double totalWeight = lowRiskCount * LOW_RISK_WEIGHT + mediumRiskCount * MEDIUM_RISK_WEIGHT + highRiskCount * HIGH_RISK_WEIGHT;
        double averageWeight = 1 - totalWeight / (lowRiskCount + mediumRiskCount + highRiskCount);
        return RiskLevelEnum.getByScoreRatio(averageWeight);
    }
}
