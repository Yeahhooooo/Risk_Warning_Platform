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
}
