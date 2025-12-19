package com.riskwarning.common.enums;

import lombok.Getter;

@Getter
public enum RegionEnum {

    CHINA("CN", "中国"),
    USA("US", "美国"),
    EUROPE("EU", "欧盟"),
    MULTI_REGION("MULTI", "多区域");

    private final String code;

    private final String description;

    RegionEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static RegionEnum fromCode(String code) {
        if (code == null) return null;
        for (RegionEnum r : values()) {
            if (r.code.equals(code) || r.name().equalsIgnoreCase(code)) return r;
        }
        throw new IllegalArgumentException("Unknown RegionEnum code: " + code);
    }


    @Override
    public String toString() {
        return description;
    }

}
