package com.riskwarning.common.enums;

public enum AssessmentStatusEnum {

    TO_BE_ASSESSED("TO_BE_ASSESSED", "待评估"),
    ASSESSING("ASSESSING", "评估中"),
    ASSESSED("ASSESSED", "已完成"),
    FAILED("FAILED", "评估失败");

    private final String code;
    private final String description;

    AssessmentStatusEnum(String code, String description) {
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
        return description;
    }
}
