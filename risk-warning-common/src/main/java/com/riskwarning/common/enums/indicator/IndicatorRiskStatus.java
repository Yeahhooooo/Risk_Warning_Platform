package com.riskwarning.common.enums.indicator;

import lombok.Getter;

/**
 * 对应 PostgreSQL 枚举类型 indicator_risk_status_enum
 * ('未评估', '已评估')
 */
@Getter
public enum IndicatorRiskStatus {
    NOT_EVALUATED("NOT_EVALUATED", "未评估"),
    EVALUATED("EVALUATED", "已评估");

    private final String code;
    // 存入数据库的中文值
    private final String dbValue;

    IndicatorRiskStatus(String code, String dbValue) {
        this.code = code;
        this.dbValue = dbValue;
    }

    public static IndicatorRiskStatus fromCode(String code) {
        if (code == null) return null;
        for (IndicatorRiskStatus s : values()) {
            if (s.code.equalsIgnoreCase(code) || s.name().equalsIgnoreCase(code)) return s;
        }
        throw new IllegalArgumentException("Unknown IndicatorRiskStatus code: " + code);
    }

    public static IndicatorRiskStatus fromDbValue(String dbValue) {
        if (dbValue == null) return null;
        for (IndicatorRiskStatus s : values()) {
            if (s.dbValue.equals(dbValue)) return s;
        }
        throw new IllegalArgumentException("Unknown IndicatorRiskStatus dbValue: " + dbValue);
    }

    @Override
    public String toString() {
        return dbValue;
    }
}

