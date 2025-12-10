package com.riskwarning.common.enums.project;

import lombok.Getter;

@Getter
public enum ProjectStatus {
    IN_PROGRESS("IN_PROGRESS", "进行中"),
    COMPLETED("COMPLETED", "已完成"),
    ARCHIVED("ARCHIVED", "已归档");

    private final String code;
    // 返回将写入数据库的值（中文）
    private final String dbValue;

    ProjectStatus(String code, String dbValue) {
        this.code = code;
        this.dbValue = dbValue;
    }

    public static ProjectStatus fromCode(String code) {
        if (code == null) return null;
        for (ProjectStatus s : values()) {
            if (s.code.equalsIgnoreCase(code) || s.name().equalsIgnoreCase(code)) return s;
        }
        throw new IllegalArgumentException("Unknown ProjectStatus code: " + code);
    }

    @Override
    public String toString() {
        return dbValue;
    }
}