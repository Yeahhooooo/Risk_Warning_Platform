package com.riskwarning.common.dto.project;


import com.riskwarning.common.dto.UserResponse;

public class ProjectMemberResponse {
    private UserResponse user;
    private String role;

    public ProjectMemberResponse() {}

    public ProjectMemberResponse(UserResponse user, String role) {
        this.user = user;
        this.role = role;
    }

    public UserResponse getUser() { return user; }
    public void setUser(UserResponse user) { this.user = user; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}