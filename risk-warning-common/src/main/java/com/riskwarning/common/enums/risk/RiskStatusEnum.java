package com.riskwarning.common.enums.risk;

public enum RiskStatusEnum {

    TO_BE_DISPOSED("TO_BE_DISPOSED", "待处置"),

    DISPOSED("DISPOSED", "已处置");

    private final String code;

    private final String description;

    RiskStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
