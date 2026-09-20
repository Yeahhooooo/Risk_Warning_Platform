package com.riskwarning.common.po.risk;


import com.riskwarning.common.enums.risk.RiskLevelEnum;
import com.riskwarning.common.enums.risk.RiskStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Risk {

    private String id;

    private Long projectId;

    private Long assessmentId;

    private String name;

    private String dimension;

    private String description;

    private RiskLevelEnum riskLevel;

    private double probability;

    private double impact;

    private double detectability;

    private RiskStatusEnum status;

    private String processingStatus;

    private String responsibleParty;

    private String[] affectedObjects;

    private String impactScope;

    private String countermeasures;

    private List<RelatedIndicator> relatedIndicators;

    /**
     * 该风险点对应的知识库指标完整路径：维度 &gt; 二级指标 &gt; 三级指标 &gt; 三级指标项。
     *
     * <p>仅写死的演示数据集（{@code FixedAssessmentData}）会填充；
     * 真实评估落库/入 ES 的风险点没有该元数据，保持 {@code null}。</p>
     */
    private String relatedIndicator;

    private LocalDateTime createAt;
}
