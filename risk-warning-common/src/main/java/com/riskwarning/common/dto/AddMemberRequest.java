package com.riskwarning.common.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddMemberRequest {
    @Getter
    @Setter
    private Long userId;


    /**
     * project_admin: 管理员
     * editor: 编辑
     * viewer: 成员
     */
    @Getter
    @Setter
    private String role;


}
