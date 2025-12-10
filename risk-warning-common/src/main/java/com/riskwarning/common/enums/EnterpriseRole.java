package com.riskwarning.common.enums;

import lombok.Getter;

@Getter
public enum EnterpriseRole {
    admin("admin", "管理员"),
    member("member", "成员");

    private final String code;
    private final String description;

    EnterpriseRole(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static EnterpriseRole from(String s) {
        if (s == null) return null;
        String normalized = s.trim();
        for (EnterpriseRole r : values()) {
            if (r.code.equalsIgnoreCase(normalized) || r.name().equalsIgnoreCase(normalized)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Unknown EnterpriseRole: " + s);
    }

    @Override
    public String toString() {
        return code;
    }
}

