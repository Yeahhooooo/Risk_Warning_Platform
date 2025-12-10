// java
package com.riskwarning.common.po.project;

import com.riskwarning.common.po.enterprise.EnterpriseUserId;
import lombok.*;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;


@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Embeddable
public class ProjectMemberId implements Serializable {
    private static final long serialVersionUID = 1L;


    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "user_id")
    private Long userId;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectMemberId)) return false;
        ProjectMemberId that = (ProjectMemberId) o;
        return Objects.equals(getProjectId(), that.getProjectId()) &&
                Objects.equals(getUserId(), that.getUserId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getProjectId(), getUserId());
    }


}
