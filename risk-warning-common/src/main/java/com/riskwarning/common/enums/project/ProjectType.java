package com.riskwarning.common.enums.project;

public enum ProjectType {
    REGULAR("REGULAR", "常规评估"),
    SPECIAL("SPECIAL", "专项评估");

    private final String code;
    private final String dbValue;

    ProjectType(String code, String dbValue) {
        this.code = code;
        this.dbValue = dbValue;
    }

    public String getCode() {
        return code;
    }

    // 返回将写入数据库的值（中文）
    public String getDbValue() {
        return dbValue;
    }

    public static ProjectType fromCode(String code) {
        if (code == null) return null;
        for (ProjectType t : values()) {
            if (t.code.equalsIgnoreCase(code) || t.name().equalsIgnoreCase(code)) return t;
        }
        throw new IllegalArgumentException("Unknown ProjectType code: " + code);
    }

    public static ProjectType fromDbValue(String dbValue) {
        if (dbValue == null) return null;
        for (ProjectType t : values()) {
            if (t.dbValue.equals(dbValue) || t.name().equalsIgnoreCase(dbValue) || t.code.equalsIgnoreCase(dbValue)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown ProjectType db value: " + dbValue);
    }

    @Override
    public String toString() {
        return dbValue;
    }
}