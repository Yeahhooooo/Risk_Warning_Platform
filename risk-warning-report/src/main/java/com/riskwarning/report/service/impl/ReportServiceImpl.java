package com.riskwarning.report.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.riskwarning.common.config.ElasticSearchConfig;
import com.riskwarning.common.enums.RiskDimensionEnum;
import com.riskwarning.common.enums.risk.RiskLevelEnum;
import com.riskwarning.common.exception.BusinessException;
import com.riskwarning.common.hardcode.FixedAssessmentData;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private IndicatorResultRepository indicatorResultRepository;

    /**
     * 判断某个 assessmentId（可带已加载的 details）是否应返回写死的模拟结论。
     *
     * <p>覆盖两种情形：</p>
     * <ul>
     *   <li>就是写死的演示评估记录（{@code assessmentId = 900001}）——不依赖数据库，恒成立；</li>
     *   <li>由 org 服务模拟评估流程产生的真实记录——其 details 被写入了固定评估详情快照。</li>
     * </ul>
     *
     * <p>第二种情形不能用「details 字符串与当前快照全等」来判断：库里的 details 是
     * <b>写入当时</b>的快照，固定数据集一旦调整（例如风险点由 16 条改为 12 条、指标得分改为两位小数），
     * 历史记录里的旧快照就与 classpath 上的当前快照不再相等；如果 details 列是 jsonb，
     * 文本往返（补空格、重排键序）同样会破坏全等。两种情况下接口都会误判为「真实评估」，
     * 转而去查 t_indicator_result / ES，最终返回空结果或旧快照。</p>
     *
     * <p>因此这里改为读快照内部的 {@code assessmentDetailVO.assessmentId / projectId}：
     * 该字段是固定数据集自身的标识，不受快照版本与文本格式影响。
     * 真实评估的 details 里这两个字段是各自的真实 id，不会命中。</p>
     */
    private boolean isSimulated(Assessment assessment) {
        if (assessment == null) {
            return false;
        }
        return isSimulated(assessment.getId(), assessment.getDetails());
    }

    private boolean isSimulated(Long assessmentId, String details) {
        if (FixedAssessmentData.matches(assessmentId)) {
            return true;
        }
        if (details == null || details.isEmpty()) {
            return false;
        }
        String fixedDetails = FixedAssessmentData.assessment().getDetails();
        if (fixedDetails != null && fixedDetails.equals(details)) {
            return true;
        }
        return detailsCarryFixedAssessment(details);
    }

    /** 解析 details，判断其中的 general 是否由固定演示数据集生成（见 {@link #isSimulated(Long, String)}）。 */
    private boolean detailsCarryFixedAssessment(String details) {
        try {
            JSONObject root = JSON.parseObject(details);
            JSONObject general = root == null ? null : root.getJSONObject("assessmentDetailVO");
            if (general == null) {
                return false;
            }
            return FixedAssessmentData.matches(general.getLong("assessmentId"))
                    || FixedAssessmentData.matches(general.getLong("projectId"));
        } catch (Exception e) {
            log.warn("details 无法解析，按真实评估处理");
            return false;
        }
    }

    /**
     * 当数据库中的评估记录带固定详情、但内存对象尚未加载 details 时，按 id 回查补齐，
     * 保证 general / indicatorResult / risk 三个接口对同一次模拟评估判断一致。
     */
    private Assessment withDetails(Assessment assessment) {
        if (assessment == null || assessment.getDetails() != null) {
            return assessment;
        }
        Assessment stored = findStored(assessment.getId());
        return stored != null ? stored : assessment;
    }

    /**
     * 按 id 回查评估记录。查询不可用或未命中时返回 null，调用方按「非模拟评估」处理，
     * 从而在未接入数据库的单元测试场景下也能保持既有行为。
     */
    private Assessment findStored(Long assessmentId) {
        if (assessmentId == null || assessmentRepository == null) {
            return null;
        }
        try {
            return assessmentRepository.findById(assessmentId).orElse(null);
        } catch (Exception e) {
            log.warn("回查评估记录失败，按非模拟评估处理，assessmentId={}", assessmentId, e);
            return null;
        }
    }

    @Autowired
    private ElasticsearchClient esClient;

    @Override
    public IndicatorDistributionVO assembleIndicatorResult(Assessment assessment) {
        // Old cached reports contain sums, so recompute scores from the original indicator records.
        return buildDistribution(assessment, indicatorResults(assessment), null);
    }

    /**
     * 读取某次评估的指标结果：命中写死/模拟的演示评估时返回固定数据，否则走原有数据库查询。
     * 这样 general / indicatorResult / risk 三个接口以及总分口径始终取自同一份数据。
     */
    private List<IndicatorResult> indicatorResults(Assessment assessment) {
        if (assessment == null) {
            return new ArrayList<>();
        }
        Assessment resolved = withDetails(assessment);
        if (isSimulated(resolved)) {
            return FixedAssessmentData.indicatorResults();
        }
        return findIndicatorResults(assessment.getId());
    }

    /** 按评估 id 查询指标结果；仓库不可用时返回空列表，避免在未接入数据库的场景下抛空指针。 */
    private List<IndicatorResult> findIndicatorResults(Long assessmentId) {
        if (assessmentId == null || indicatorResultRepository == null) {
            return new ArrayList<>();
        }
        List<IndicatorResult> rows = indicatorResultRepository.findByAssessmentId(assessmentId);
        return rows != null ? rows : new ArrayList<>();
    }

    private IndicatorDistributionVO buildDistribution(Assessment assessment, List<IndicatorResult> rows,
                                                       RiskDimensionEnum dimension) {
        IndicatorDistributionVO result = IndicatorDistributionVO.builder()
                .assessmentId(assessment.getId()).assessmentTime(assessment.getAssessmentDate())
                .riskDimensionEnum(dimension).totalScore(AssessmentScores.percentage(rows)).maxScore(100.0)
                .totalCount(rows.size()).riskTriggeredCount(0).safeCount(0)
                // 枚举键必须用 EnumMap：HashMap 的迭代顺序取决于枚举常量的 identity hash，
                // 每次 JVM 启动都可能不同，会让同一份结果的 JSON 键序漂移（快照一致性校验随机失败）。
                .scoreDistributions(new ArrayList<>()).dimensionDistributions(new EnumMap<>(RiskDimensionEnum.class))
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
                    .relatedIndicator(row.getRelatedIndicator())
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
        // 命中写死/模拟评估时使用固定指标；否则按 id 查库。
        // 写死的演示评估（id 900001）不必回查数据库，先判掉可省掉一次无意义的查询。
        boolean simulated = FixedAssessmentData.matches(assessmentId);
        if (!simulated) {
            Assessment stored = findStored(assessmentId);
            simulated = isSimulated(assessmentId, stored != null ? stored.getDetails() : null);
        }
        List<IndicatorResult> indicatorResults = simulated
                ? FixedAssessmentData.indicatorResults()
                : findIndicatorResults(assessmentId);
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
        // 写死的演示评估与模拟评估始终按固定指标与风险实时组装，不解析 details 快照，
        // 保证 general 与 indicatorResult / risk 三个接口口径完全一致。
        if (isSimulated(withDetails(assessment))) {
            return assembleGeneralWithRisks(assessment, FixedAssessmentData.risks());
        }
        // 检查assessment的detail属性是否已经存在general信息，若存在则直接反序列化返回，避免重复计算
        if(assessment.getDetails() != null && !assessment.getDetails().isEmpty()) {
            try{
                AssessmentGeneralDetails assessmentGeneralDetails = JSON.parseObject(assessment.getDetails(), AssessmentGeneralDetails.class);
                AssessmentDetailVO general = assessmentGeneralDetails.getAssessmentDetailVO();
                general.getOverallResult().setOverallScore(AssessmentScores.percentage(
                        indicatorResults(assessment)));
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
        List<IndicatorResult> indicatorRows = indicatorResults(assessment);
        AssessmentDetailVO assessmentDetailVO = new AssessmentDetailVO();
        assessmentDetailVO.setProjectId(assessment.getProjectId());
        assessmentDetailVO.setAssessmentId(assessment.getId());
        assessmentDetailVO.setAssessmentDate(assessment.getAssessmentDate());
        assessmentDetailVO.setOverallResult(OverallResult.builder()
                .overallScore(AssessmentScores.percentage(indicatorRows))
                .overallRiskLevel(assessment.getOverallRiskLevel())
                .status(assessment.getStatus())
                .build()
        );
        assessmentDetailVO.setRiskSummary(new RiskSummary());
        assessmentDetailVO.setIndicatorOverview(new IndicatorOverview());
        // 同上：枚举键用 EnumMap 保证键序确定（按枚举声明顺序）
        assessmentDetailVO.setDimensionRiskDistribution(new EnumMap<>(RiskDimensionEnum.class));
        for(RiskDimensionEnum riskDimensionEnum : RiskDimensionEnum.values()) {
            assessmentDetailVO.getDimensionRiskDistribution().put(riskDimensionEnum, new DimensionRiskDistribution());
        }
        assessmentDetailVO.getIndicatorOverview().setBehaviorIndicators(risks.size());
        int triggeredIndicators = 0;
        for (IndicatorResult row : indicatorRows) {
            if (Boolean.TRUE.equals(row.getRiskTriggered())) {
                triggeredIndicators++;
            }
        }
        assessmentDetailVO.getIndicatorOverview().setRiskTriggeredIndicators(triggeredIndicators);
        assessmentDetailVO.getIndicatorOverview().setSafeIndicators(indicatorRows.size() - triggeredIndicators);
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
        // 写死的演示评估直接从固定数据集取风险，不再访问 ES
        if (FixedAssessmentData.matches(assessmentId)) {
            return FixedAssessmentData.risks();
        }
        // 模拟评估流程落库的真实记录：details 内带固定数据集标记，同样返回固定风险
        Assessment stored = findStored(assessmentId);
        if (isSimulated(assessmentId, stored != null ? stored.getDetails() : null)) {
            return FixedAssessmentData.risks();
        }
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
