package com.riskwarning.common.po.report;


import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.common.enums.risk.RiskLevelEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_assessment_result")
@org.hibernate.annotations.TypeDef(name = "pgsql_enum", typeClass = com.riskwarning.common.utils.PostgreSQLEnumType.class)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long projectId;

    private LocalDateTime assessmentDate;

    private Double overallScore;

    private RiskLevelEnum overallRiskLevel;

    private String details;

    private String recommendations;

    @Type(type = "pgsql_enum")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "assessment_status_enum")
    private AssessmentStatusEnum status;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "failure_stage", length = 64)
    private String failureStage;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    /** Stable identity for uploads, including retries before file merging succeeds. */
    @Column(name = "upload_task_id", length = 64, unique = true)
    private String uploadTaskId;

    private LocalDateTime createdAt;
}
