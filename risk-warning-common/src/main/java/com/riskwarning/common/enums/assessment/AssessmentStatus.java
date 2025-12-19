package com.riskwarning.common.enums.assessment;

import lombok.Getter;

/**
 * 对应 PostgreSQL 枚举类型 assessment_status_enum
 * ('待评估', '评估中', '已完成', '评估失败')
 */
@Getter
public enum AssessmentStatus {
    PENDING("PENDING", "待评估"),
    IN_PROGRESS("IN_PROGRESS", "评估中"),
    COMPLETED("COMPLETED", "已完成"),
    FAILED("FAILED", "评估失败");

    private final String code;
    // 存入数据库的中文值
    private final String dbValue;

    AssessmentStatus(String code, String dbValue) {
        this.code = code;
        this.dbValue = dbValue;
    }

    public static AssessmentStatus fromCode(String code) {
        if (code == null) return null;
        for (AssessmentStatus s : values()) {
            if (s.code.equalsIgnoreCase(code) || s.name().equalsIgnoreCase(code)) return s;
        }
        throw new IllegalArgumentException("Unknown AssessmentStatus code: " + code);
    }

    public static AssessmentStatus fromDbValue(String dbValue) {
        if (dbValue == null) return null;
        for (AssessmentStatus s : values()) {
            if (s.dbValue.equals(dbValue)) return s;
        }
        throw new IllegalArgumentException("Unknown AssessmentStatus dbValue: " + dbValue);
    }

    @Override
    public String toString() {
        return dbValue;
    }
}

