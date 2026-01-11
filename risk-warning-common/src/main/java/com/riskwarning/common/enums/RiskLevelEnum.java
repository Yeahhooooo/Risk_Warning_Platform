package com.riskwarning.common.enums;

public enum RiskLevelEnum {

    LOW_RISK("LOW_RISK", "低风险"),

    MEDIUM_RISK("MEDIUM_RISK", "中风险"),

    HIGH_RISK("HIGH_RISK", "高风险");

    private final String code;

    private final String description;

    RiskLevelEnum(String code, String description) {
        this.code = code;
        this.description = description;
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
}
