package com.riskwarning.common.enums;

public enum RegionEnum {

    CHINA("CN", "中国"),
    USA("US", "美国"),
    EUROPE("EU", "欧洲"),
    MULTI_REGION("MULTI", "多区域");

    private final String code;

    private final String description;

    RegionEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    // 返回将写入数据库的值（中文）
    public String getDbValue() {
        return description;
    }

    public static RegionEnum fromCode(String code) {
        if (code == null) return null;
        for (RegionEnum r : values()) {
            if (r.code.equals(code) || r.name().equalsIgnoreCase(code)) return r;
        }
        throw new IllegalArgumentException("Unknown RegionEnum code: " + code);
    }

    // 从数据库读取的中文值反序列化为枚举
    public static RegionEnum fromDbValue(String dbValue) {
        if (dbValue == null) return null;
        for (RegionEnum r : values()) {
            if (r.description.equals(dbValue) || r.name().equalsIgnoreCase(dbValue) || r.code.equalsIgnoreCase(dbValue)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Unknown RegionEnum db value: " + dbValue);
    }


    @Override
    public String toString() {
        return description;
    }

}
