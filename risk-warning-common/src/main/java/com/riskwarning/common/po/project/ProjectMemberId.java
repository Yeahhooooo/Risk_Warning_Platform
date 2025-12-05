// java
package com.riskwarning.common.po.project;

import lombok.*;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProjectMemberId implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long projectId;
    private Long userId;
}
