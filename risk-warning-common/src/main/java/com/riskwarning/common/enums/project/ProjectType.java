package com.riskwarning.common.enums.project;

import lombok.Getter;

@Getter
public enum ProjectType {
    REGULAR("REGULAR", "常规评估"),
    SPECIAL("SPECIAL", "专项评估");

    private final String code;
    // 返回将写入数据库的值（中文）
    private final String dbValue;

    ProjectType(String code, String dbValue) {
        this.code = code;
        this.dbValue = dbValue;
    }

    public static ProjectType fromCode(String code) {
        if (code == null) return null;
        for (ProjectType t : values()) {
            if (t.code.equalsIgnoreCase(code) || t.name().equalsIgnoreCase(code)) return t;
        }
        throw new IllegalArgumentException("Unknown ProjectType code: " + code);
    }

    @Override
    public String toString() {
        return dbValue;
    }
}