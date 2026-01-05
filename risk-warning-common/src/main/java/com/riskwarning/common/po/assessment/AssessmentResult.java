package com.riskwarning.common.po.assessment;

import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import com.riskwarning.common.enums.assessment.AssessmentStatus;
import com.riskwarning.common.utils.PostgreSQLEnumType;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 对应数据库表: public.t_assessment_result
 */
@Entity
@Table(name = "t_assessment_result")
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "assessment_date")
    private OffsetDateTime assessmentDate;

    @Column(name = "overall_score")
    private BigDecimal overallScore;

    @Column(name = "overall_risk_level")
    private Integer overallRiskLevel;

    // 存储 JSONB 原文（作为字符串存储），数据库列类型为 jsonb
    @Type(type = "jsonb")
    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "recommendations")
    private String recommendations;

    // 对应数据库中的 assessment_status_enum
    @Type(type = "pgsql_enum")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "assessment_status_enum")
    private AssessmentStatus status;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
