package com.riskwarning.common.enums.project;

import lombok.Getter;

/**
 * 项目角色枚举
 */
@Getter
public enum ProjectRole {
    PROJECT_ADMIN("project_admin", "项目管理员"),
    EDITOR("editor", "编辑者"),
    VIEWER("viewer", "查看者");

    private final String code;
    private final String description;

    ProjectRole(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public  static ProjectRole from(String code) {

        if (code == null) return null;
        String normalized = code.trim();

        for (ProjectRole role : ProjectRole.values()) {
            if (role.getCode().equalsIgnoreCase(normalized) || role.name().equalsIgnoreCase(normalized)) {
                return role;
            }

        }
        throw new IllegalArgumentException("Unknown ProjectRole: " + code);
    }



    @Override
    public String toString() {
        return code;
    }


}
