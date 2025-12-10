package com.riskwarning.common.po.project;

import com.riskwarning.common.enums.project.ProjectRole;
import com.riskwarning.common.po.user.User;
import com.riskwarning.common.utils.PostgreSQLEnumType;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "t_project_member")
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
public class ProjectMember {

    @EmbeddedId
    private ProjectMemberId id;

    @MapsId("projectId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    @Setter
    @Getter
    private Project project;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @Setter
    @Getter
    private User user;

    @Type(type = "pgsql_enum")
    @Enumerated(EnumType.STRING)
    @Column(name = "role", columnDefinition = "project_role", nullable = false)
    @Setter
    @Getter
    private ProjectRole role;
}