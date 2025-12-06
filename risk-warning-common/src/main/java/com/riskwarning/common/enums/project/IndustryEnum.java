package com.riskwarning.common.enums.project;

public enum IndustryEnum {
    SUPPLY_CHAIN("SUPPLY_CHAIN", "供应链管理"),
    MARKETING("MARKETING", "市场营销与广告"),
    HR("HR", "人力资源与劳动关系"),
    CROSS_BORDER("CROSS_BORDER", "跨境交易与支付"),
    DATA_PRIVACY("DATA_PRIVACY", "数据隐私与网络安全"),
    ANTI_TRUST("ANTI_TRUST", "反垄断与不正当竞争"),
    IP("IP", "知识产权"),
    FINANCE_TAX("FINANCE_TAX", "财务与税务");

    private final String code;
    private final String dbValue;

    IndustryEnum(String code, String dbValue) {
        this.code = code;
        this.dbValue = dbValue;
    }

    public String getCode() {
        return code;
    }

    // 返回将写入/存储到数据库的值（中文）
    public String getDbValue() {
        return dbValue;
    }

    public static IndustryEnum fromCode(String code) {
        if (code == null) return null;
        for (IndustryEnum e : values()) {
            if (e.code.equalsIgnoreCase(code) || e.name().equalsIgnoreCase(code)) return e;
        }
        throw new IllegalArgumentException("Unknown IndustryEnum code: " + code);
    }

    public static IndustryEnum fromDbValue(String dbValue) {
        if (dbValue == null) return null;
        for (IndustryEnum e : values()) {
            if (e.dbValue.equals(dbValue) || e.name().equalsIgnoreCase(dbValue) || e.code.equalsIgnoreCase(dbValue)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unknown IndustryEnum db value: " + dbValue);
    }

    @Override
    public String toString() {
        return dbValue;
    }
}