// java
package com.riskwarning.common.po.project;

import com.riskwarning.common.enums.project.IndustryEnum;
import com.riskwarning.common.enums.project.ProjectOrientedUserEnum;
import com.riskwarning.common.enums.project.ProjectStatus;
import com.riskwarning.common.enums.RegionEnum;
import com.riskwarning.common.enums.project.ProjectType;
import com.riskwarning.common.utils.PostgreSQLEnumType;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "t_project")
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enterprise_id", nullable = false)
    private Long enterpriseId;

    @Column(nullable = false)
    private String name;

    @Type(type = "pgsql_enum")
    @Enumerated(EnumType.STRING)
    @Column(name = "type", columnDefinition = "project_type")
    private ProjectType type;

    @Type(type = "pgsql_enum")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "project_status", nullable = false)
    private ProjectStatus status;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "planned_completion_date")
    private LocalDate plannedCompletionDate;

    @Column(name = "actual_completion_date")
    private LocalDate actualCompletionDate;

    @Type(type = "pgsql_enum")
    @Enumerated(EnumType.STRING)
    @Column(name = "industry", columnDefinition = "industry_enum")
    private IndustryEnum industry;

    @Type(type = "pgsql_enum")
    @Enumerated(EnumType.STRING)
    @Column(name = "region", columnDefinition = "region_enum")
    private RegionEnum region;

    @Type(type = "pgsql_enum")
    @Enumerated(EnumType.STRING)
    @Column(name = "oriented_user", columnDefinition = "project_oriented_user_enum")
    private ProjectOrientedUserEnum orientedUser;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}