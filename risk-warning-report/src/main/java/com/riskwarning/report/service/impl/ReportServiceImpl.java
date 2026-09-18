package com.riskwarning.report.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.config.ElasticSearchConfig;
import com.riskwarning.common.enums.RiskDimensionEnum;
import com.riskwarning.common.enums.risk.RiskLevelEnum;
import com.riskwarning.common.exception.BusinessException;
import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.po.risk.Risk;
import com.riskwarning.report.entity.vo.AssessmentGeneralDetails;
import com.riskwarning.report.entity.vo.general.*;
import com.riskwarning.report.entity.vo.indicator.IndicatorDistributionVO;
import com.riskwarning.report.entity.vo.indicator.ScoreRatioDistributionItemVO;
import com.riskwarning.report.entity.vo.risk.RiskVO;
import com.riskwarning.report.repository.AssessmentRepository;
import com.riskwarning.report.repository.IndicatorResultRepository;
import com.riskwarning.report.service.ReportService;
import com.riskwarning.report.util.AssessmentScores;
import com.riskwarning.report.util.ReportRiskDimensions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private IndicatorResultRepository indicatorResultRepository;

    @Autowired
    private ElasticsearchClient esClient;

    @Override
    public IndicatorDistributionVO assembleIndicatorResult(Assessment assessment) {
        // Old cached reports contain sums, so recompute scores from the original indicator records.
        return buildDistribution(assessment, indicatorResultRepository.findByAssessmentId(assessment.getId()), null);
    }

    private IndicatorDistributionVO buildDistribution(Assessment assessment, List<IndicatorResult> rows,
                                                       RiskDimensionEnum dimension) {
        IndicatorDistributionVO result = IndicatorDistributionVO.builder()
                .assessmentId(assessment.getId()).assessmentTime(assessment.getAssessmentDate())
                .riskDimensionEnum(dimension).totalScore(AssessmentScores.percentage(rows)).maxScore(100.0)
                .totalCount(rows.size()).riskTriggeredCount(0).safeCount(0)
                .scoreDistributions(new ArrayList<>()).dimensionDistributions(new HashMap<>())
                .indicatorScores(new ArrayList<>()).build();
        List<List<IndicatorResult>> buckets = new ArrayList<>();
        for (int i = 0; i < 4; i++) buckets.add(new ArrayList<>());
        java.util.Map<RiskDimensionEnum, List<IndicatorResult>> dimensions = new java.util.LinkedHashMap<>();
        for (IndicatorResult row : rows) {
            boolean triggered = Boolean.TRUE.equals(row.getRiskTriggered());
            if (triggered) result.setRiskTriggeredCount(result.getRiskTriggeredCount() + 1);
            else result.setSafeCount(result.getSafeCount() + 1);
            Double ratio = AssessmentScores.ratio(row);
            result.getIndicatorScores().add(com.riskwarning.report.entity.vo.indicator.IndicatorScoreVO.builder()
                    .indicatorId(row.getIndicatorEsId()).indicatorName(row.getIndicatorName()).dimension(ReportRiskDimensions.normalize(row.getDimension()))
                    .relatedBehaviors(behaviorDescriptions(row))
                    .score(AssessmentScores.percentage(row)).maxScore(100.0).riskTriggered(triggered)
                    .riskLevel(ratio == null ? null : RiskLevelEnum.getByScoreRatio(ratio)).build());
            if (ratio != null) buckets.get(Math.min(3, (int) (ratio / 0.25))).add(row);
            if (dimension == null) dimensions.computeIfAbsent(ReportRiskDimensions.parse(row.getDimension()),
                    key -> new ArrayList<>()).add(row);
        }
        for (int i = 0; i < 4; i++) {
            List<IndicatorResult> bucket = buckets.get(i);
            int triggered = (int) bucket.stream().filter(row -> Boolean.TRUE.equals(row.getRiskTriggered())).count();
            result.getScoreDistributions().add(ScoreRatioDistributionItemVO.builder()
                    .startScoreRatio(i * 0.25).endScoreRatio((i + 1) * 0.25)
                    .ratio(rows.isEmpty() ? 0.0 : (double) bucket.size() / rows.size())
                    .totalScore(AssessmentScores.percentage(bucket)).totalCount(bucket.size())
                    .riskTriggeredCount(triggered).safeCount(bucket.size() - triggered).build());
        }
        dimensions.forEach((key, values) -> result.getDimensionDistributions().put(key, buildDistribution(assessment, values, key)));
        return result;
    }

    private List<String> behaviorDescriptions(IndicatorResult row) {
        java.util.Set<String> descriptions = new java.util.LinkedHashSet<>();
        if (row.getCalculationDetails() != null && row.getCalculationDetails().getRelatedIndicators() != null) {
            for (com.riskwarning.common.po.risk.RelatedIndicator indicator : row.getCalculationDetails().getRelatedIndicators()) {
                if (indicator == null || indicator.getRelatedBehaviors() == null) continue;
                for (com.riskwarning.common.po.risk.RelatedBehavior behavior : indicator.getRelatedBehaviors()) {
                    if (behavior != null && behavior.getDescription() != null && !behavior.getDescription().trim().isEmpty()) {
                        descriptions.add(behavior.getDescription());
                    }
                }
            }
        }
        return new ArrayList<>(descriptions);
    }

    @Override
    public List<RiskVO> assembleRisk(Long assessmentId) {
        List<Risk> risks = fetchRisksFromES(assessmentId);
        List<IndicatorResult> indicatorResults = indicatorResultRepository.findByAssessmentId(assessmentId);
        List<RiskVO> riskVOList = new ArrayList<>();
        for(Risk risk : risks) {
            RiskVO riskVO = new RiskVO();
            BeanUtils.copyProperties(risk, riskVO);
            riskVO.setDimension(ReportRiskDimensions.normalize(risk.getDimension()));
            riskVO.setMaxScore(100.0);
            if (risk.getRelatedIndicators() != null) {
                java.util.Set<String> ids = new java.util.HashSet<>();
                List<com.riskwarning.common.po.risk.RelatedIndicator> evidence = new ArrayList<>();
                for (com.riskwarning.common.po.risk.RelatedIndicator original : risk.getRelatedIndicators()) {
                    if (original == null) continue;
                    ids.add(original.getIndicatorId());
                    com.riskwarning.common.po.risk.RelatedIndicator copy = new com.riskwarning.common.po.risk.RelatedIndicator();
                    BeanUtils.copyProperties(original, copy);
                    // Stored behavior evidence is a 0-1 compliance ratio, not an absolute indicator score.
                    Double raw = original.getScore();
                    copy.setScore(raw == null || !Double.isFinite(raw) ? null
                            : AssessmentScores.round(Math.max(0, Math.min(1, raw)) * 100));
                    copy.setMaxScore(100.0);
                    evidence.add(copy);
                }
                riskVO.setRelatedIndicators(evidence);
                riskVO.setScore(AssessmentScores.percentage(indicatorResults.stream()
                        .filter(row -> ids.contains(row.getIndicatorEsId())).collect(java.util.stream.Collectors.toList())));
            }

            if (risk.getRiskLevel() != null) {
                riskVO.setRiskLevel(risk.getRiskLevel().name());
            }
            if (risk.getStatus() != null) {
                riskVO.setStatus(risk.getStatus().name());
            }
            if (riskVO.getProcessingStatus() == null || riskVO.getProcessingStatus().isEmpty()) {
                riskVO.setProcessingStatus("成功");
            }
            riskVOList.add(riskVO);
        }
        return riskVOList;
    }

    @Override
    public AssessmentDetailVO assembleGeneral(Assessment assessment) {
        // 检查assessment的detail属性是否已经存在general信息，若存在则直接反序列化返回，避免重复计算
        if(assessment.getDetails() != null && !assessment.getDetails().isEmpty()) {
            try{
                AssessmentGeneralDetails assessmentGeneralDetails = JSON.parseObject(assessment.getDetails(), AssessmentGeneralDetails.class);
                AssessmentDetailVO general = assessmentGeneralDetails.getAssessmentDetailVO();
                general.getOverallResult().setOverallScore(AssessmentScores.percentage(
                        indicatorResultRepository.findByAssessmentId(assessment.getId())));
                return general;
            } catch (Exception e) {
                log.error("评估报告已存在详细信息但反序列化失败", e);
                throw new BusinessException("评估报告已存在详细信息但反序列化失败");
            }
        }
        List<Risk> risks = fetchRisksFromES(assessment.getId());
        return assembleGeneralWithRisks(assessment, risks);
    }

    @Override
    public AssessmentDetailVO assembleGeneral(Assessment assessment, List<Risk> risks) {
        return assembleGeneralWithRisks(assessment, risks != null ? risks : new ArrayList<>());
    }

    /**
     * 根据 assessment 与风险列表构建汇总 VO（不查 ES），并正确设置 totalRisks
     */
    private AssessmentDetailVO assembleGeneralWithRisks(Assessment assessment, List<Risk> risks) {
        AssessmentDetailVO assessmentDetailVO = new AssessmentDetailVO();
        assessmentDetailVO.setProjectId(assessment.getProjectId());
        assessmentDetailVO.setAssessmentId(assessment.getId());
        assessmentDetailVO.setAssessmentDate(assessment.getAssessmentDate());
        assessmentDetailVO.setOverallResult(OverallResult.builder()
                .overallScore(AssessmentScores.percentage(indicatorResultRepository.findByAssessmentId(assessment.getId())))
                .overallRiskLevel(assessment.getOverallRiskLevel())
                .status(assessment.getStatus())
                .build()
        );
        assessmentDetailVO.setRiskSummary(new RiskSummary());
        assessmentDetailVO.setIndicatorOverview(new IndicatorOverview());
        assessmentDetailVO.setDimensionRiskDistribution(new HashMap<>());
        for(RiskDimensionEnum riskDimensionEnum : RiskDimensionEnum.values()) {
            assessmentDetailVO.getDimensionRiskDistribution().put(riskDimensionEnum, new DimensionRiskDistribution());
        }
        assessmentDetailVO.getIndicatorOverview().setBehaviorIndicators(risks.size());
        for(Risk risk : risks) {
            RiskDimensionEnum dimensionEnum = ReportRiskDimensions.parse(risk.getDimension());
            RiskLevelEnum riskLevelEnum = risk.getRiskLevel();
            if (riskLevelEnum == null) {
                log.warn("[assembleGeneral] risk_level is null for risk id={}, skipping risk level aggregation", risk.getId());
                continue;
            }
            DimensionRiskDistribution distribution = assessmentDetailVO.getDimensionRiskDistribution().get(dimensionEnum);
            distribution.setRiskCount(nullSafe(distribution.getRiskCount()) + 1);
            switch (riskLevelEnum) {
                case LOW_RISK:
                    assessmentDetailVO.getRiskSummary().setLowRiskCount(nullSafe(assessmentDetailVO.getRiskSummary().getLowRiskCount()) + 1);
                    distribution.setLowRiskCount(nullSafe(distribution.getLowRiskCount()) + 1);
                    break;
                case MEDIUM_RISK:
                    assessmentDetailVO.getRiskSummary().setMediumRiskCount(nullSafe(assessmentDetailVO.getRiskSummary().getMediumRiskCount()) + 1);
                    distribution.setMediumRiskCount(nullSafe(distribution.getMediumRiskCount()) + 1);
                    break;
                case HIGH_RISK:
                    assessmentDetailVO.getRiskSummary().setHighRiskCount(nullSafe(assessmentDetailVO.getRiskSummary().getHighRiskCount()) + 1);
                    distribution.setHighRiskCount(nullSafe(distribution.getHighRiskCount()) + 1);
                    break;
                default:
                    log.error("riskLevelEnum error, unexpected value: {}", riskLevelEnum);
            }
        }
        // 风险总数 = 高+中+低（与前端展示一致）
        int total = nullSafe(assessmentDetailVO.getRiskSummary().getHighRiskCount())
                + nullSafe(assessmentDetailVO.getRiskSummary().getMediumRiskCount())
                + nullSafe(assessmentDetailVO.getRiskSummary().getLowRiskCount());
        assessmentDetailVO.getRiskSummary().setTotalRisks(total);
        return assessmentDetailVO;
    }

    private static int nullSafe(Integer n) {
        return n != null ? n : 0;
    }


    public List<Risk> fetchRisksFromES(Long assessmentId) {
        try {
            log.info("[Fetching Risks] assessmentId={}, dimension={}, riskLevel={}",
                    assessmentId);

            SearchResponse<Risk> resp = esClient.search(s -> s
                            .index(ElasticSearchConfig.RISK_INDEX)
                            .size(1000)  // 假设一个项目不会超过1000个behaviors，如需要可以改成scroll
                            .query(q -> q
                                    .bool(b -> b
                                            .must(m1 -> m1.term(t -> t.field("assessment_id").value(assessmentId)))
                                    )
                            )
                            .sort(sort -> sort
                                    .field(f -> f
                                            .field("create_at")
                                            .order(SortOrder.Desc)
                                    )
                            ),
                    Risk.class
            );

            List<Risk> risks = new ArrayList<>();
            if (resp != null && resp.hits() != null && resp.hits().hits() != null) {
                for (Hit<Risk> hit : resp.hits().hits()) {
                    Risk risk = hit.source();
                    if (risk != null) {
                        risks.add(risk);
                        log.debug("[Risk Fetched] assessmentId={}, riskId={}", assessmentId, risk.getId());
                    }
                }
            }

            log.info("[Fetching Risks Completed] assessmentId={}, totalRisks={}",
                    assessmentId, risks.size());
            return risks;
        } catch (Exception e) {
            log.error("[Fetching Risks Failed] assessmentId={}", assessmentId, e);
            return Collections.emptyList();
        }
    }

    // TODO: 根据某个指标结果查询相关风险，风险对应法规的详细信息接口

}
