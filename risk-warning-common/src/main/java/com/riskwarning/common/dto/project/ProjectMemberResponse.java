package com.riskwarning.common.dto.project;


import com.riskwarning.common.dto.UserResponse;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ProjectMemberResponse {
    private UserResponse user;
    private String role;

    public ProjectMemberResponse() {}

    public ProjectMemberResponse(UserResponse user, String role) {
        this.user = user;
        this.role = role;
    }

}