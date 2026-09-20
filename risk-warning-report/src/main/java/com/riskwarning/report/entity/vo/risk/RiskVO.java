package com.riskwarning.report.entity.vo.risk;


import com.riskwarning.common.po.risk.RelatedIndicator;
import com.riskwarning.common.po.risk.RelatedRegulation;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RiskVO {
    private String id;

    private Long projectId;

    private Long assessmentId;

    private String name;

    private String dimension;

    private String description;

    private String riskLevel;

    /** Aggregate indicator score on the report's 0-100 scale. */
    private Double score;

    private Double maxScore;

    private double probability;

    private double impact;

    private double detectability;

    private String status;

    private String processingStatus;

    private String responsibleParty;

    private String[] affectedObjects;

    private String impactScope;

    private String countermeasures;

    private List<RelatedIndicator> relatedIndicators;

    /**
     * 该风险点对应的知识库指标完整路径：维度 &gt; 二级指标 &gt; 三级指标 &gt; 三级指标项。
     *
     * <p>例如「企业关联方风险 &gt; 合作伙伴（合资方、战略合作方） &gt; 合作方违规 &gt; 企业合作伙伴是否存在违法违规行为」。
     * 写死的演示数据集恒有值；真实评估的风险点没有该元数据，返回 {@code null}。</p>
     */
    private String relatedIndicator;

    private List<RelatedRegulation> relatedRegulations;

    private LocalDateTime createAt;
}
