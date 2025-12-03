package com.riskwarning.common.enums;

public enum RuleTypeEnum {

    BINARY("BINARY", "二元规则"),

    RANGE("RANGE", "范围规则");

    private final String code;

    private final String description;

    RuleTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return this.code;
    }
}
