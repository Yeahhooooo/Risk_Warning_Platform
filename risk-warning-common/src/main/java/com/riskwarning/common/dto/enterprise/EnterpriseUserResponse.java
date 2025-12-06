package com.riskwarning.common.dto.enterprise;

import com.riskwarning.common.dto.UserResponse;

public class EnterpriseUserResponse {
    private UserResponse user;
    private String role;

    public EnterpriseUserResponse() {}

    public EnterpriseUserResponse(UserResponse user, String role) {
        this.user = user;
        this.role = role;
    }

    public UserResponse getUser() {
        return user;
    }

    public void setUser(UserResponse user) {
        this.user = user;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
