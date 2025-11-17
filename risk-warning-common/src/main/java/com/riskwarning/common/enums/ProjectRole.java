package com.riskwarning.common.enums;

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
}
