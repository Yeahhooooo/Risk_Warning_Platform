package com.riskwarning.common.enums;

/**
 * 企业角色枚举
 */
public enum EnterpriseRole {
    ADMIN("admin", "管理员"),
    MEMBER("member", "成员");

    private final String code;
    private final String description;

    EnterpriseRole(String code, String description) {
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
