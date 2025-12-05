package com.riskwarning.common.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddMemberRequest {
    @Getter
    @Setter
    private Long userId;

    @Getter
    @Setter
    private String role; // e.g. "member" or "admin"


}
