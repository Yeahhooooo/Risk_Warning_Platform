package com.riskwarning.common.enums.project;

import lombok.Getter;

@Getter
public enum ProjectOrientedUserEnum {
    GOVERNMENT("GOVERNMENT", "政府机构与官员"),
    SOE("SOE", "国有企业"),
    SUPPLIER("SUPPLIER", "关键供应商/承包商"),
    CUSTOMER("CUSTOMER", "客户"),
    EMPLOYEE("EMPLOYEE", "员工"),
    INDIVIDUAL("INDIVIDUAL", "个人用户"),
    PUBLIC("PUBLIC", "公众");

    private final String code;
    // 返回将写入数据库的值（中文）
    private final String dbValue;

    ProjectOrientedUserEnum(String code, String dbValue) {
        this.code = code;
        this.dbValue = dbValue;
    }

    public static ProjectOrientedUserEnum fromCode(String code) {
        if (code == null) return null;
        for (ProjectOrientedUserEnum p : values()) {
            if (p.code.equalsIgnoreCase(code) || p.name().equalsIgnoreCase(code)) return p;
        }
        throw new IllegalArgumentException("Unknown ProjectOrientedUserEnum code: " + code);
    }

    @Override
    public String toString() {
        return dbValue;
    }
}