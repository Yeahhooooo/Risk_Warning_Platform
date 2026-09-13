package com.riskwarning.common.enums.risk;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RiskStatusEnum {

    TO_BE_DISPOSED("TO_BE_DISPOSED", "待处置"),

    DISPOSED("DISPOSED", "已处置");

    private final String code;

    private final String description;

    RiskStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    @JsonCreator
    public static RiskStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (RiskStatusEnum e : RiskStatusEnum.values()) {
            if (e.code.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return this.code;
    }
}
