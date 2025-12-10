package com.riskwarning.common.dto.enterprise;

import com.riskwarning.common.dto.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class EnterpriseUserResponse {
    private UserResponse user;
    private String role;

    public EnterpriseUserResponse() {}

    public EnterpriseUserResponse(UserResponse user, String role) {
        this.user = user;
        this.role = role;
    }

}
