package com.riskwarning.common.po.project;

import com.riskwarning.common.po.enterprise.Enterprise;
import lombok.*;
import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "t_project")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enterprise_id", nullable = false)
    private Enterprise enterprise;

    @Column(nullable = false)
    private String name;

    @Column(name = "type")
    private String type; // 对应 DB 的 project_type

    @Column(nullable = false)
    private String status; // 对应 DB 的 project_status（如 '进行中'）

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "planned_completion_date")
    private LocalDate plannedCompletionDate;

    @Column(name = "actual_completion_date")
    private LocalDate actualCompletionDate;

    @Column
    private String industry; // 对应 industry_enum

    @Column
    private String region; // 对应 region_enum

    @Column(name = "oriented_user")
    private String orientedUser; // 对应 project_oriented_user_enum

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
