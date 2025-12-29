package com.riskwarning.processing.service;

import com.riskwarning.common.enums.indicator.IndicatorRiskStatus;
import com.riskwarning.common.po.behavior.Behavior;
import com.riskwarning.common.po.indicator.Indicator;
import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.po.regulation.Regulation;
import com.riskwarning.common.po.assessment.AssessmentResult;
import com.riskwarning.processing.dto.MappingResult;
import com.riskwarning.processing.repository.IndicatorResultRepository;
import com.riskwarning.processing.repository.AssessmentResultRepository;
import com.riskwarning.processing.util.behavior.FallbackCalculator;
import com.riskwarning.processing.util.behavior.QualitativeCalculator;
import com.riskwarning.processing.util.behavior.QuantitativeCalculator;
import com.riskwarning.processing.util.behavior.RegWeightCalculator;
import com.riskwarning.processing.util.behavior.SimilarityCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.riskwarning.common.enums.assessment.AssessmentStatus;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class BehaviorProcessingService {

    private static final double REG_APPLICABILITY_THRESHOLD = 0.15;
    private static final double REG_TO_INDICATOR_THRESHOLD = 0.2;

    // 新增：注入 ElasticsearchClient 与索引名配置
    private final ElasticsearchClient esClient;

    private final IndicatorResultRepository indicatorResultRepository;

    private final AssessmentResultRepository assessmentResultRepository;

    private final ObjectMapper objectMapper;

    @Value("${es.index.indicator:t_indicator}")
    private String indicatorIndex;

    @Value("${es.index.regulation:t_regulation}")
    private String regulationIndex;

    private static final Logger log = LoggerFactory.getLogger(BehaviorProcessingService.class);

    @Autowired
    public BehaviorProcessingService(ElasticsearchClient esClient,
                                     IndicatorResultRepository indicatorResultRepository,
                                     AssessmentResultRepository assessmentResultRepository,
                                     ObjectMapper objectMapper) {
        this.esClient = esClient;
        this.indicatorResultRepository = indicatorResultRepository;
        this.assessmentResultRepository = assessmentResultRepository;
        this.objectMapper = objectMapper;
    }

    // 改名：入口方法，从 behavior 中获取 projectId，并推算下一个 assessmentId，直接计算并入库
    @Transactional
    public MappingResult processAndPersistFromBehavior(Behavior behavior) {
        Long projectId = null;

        if (behavior != null) {
            projectId=behavior.getProject_id();
        }
        if (projectId == null) {
            throw new IllegalArgumentException("Behavior must contain projectId");
        }

        // 先在 assessment 表中创建一条记录，获取数据库自增 id 作为 assessmentId
        AssessmentResult ar = AssessmentResult.builder()
                .projectId(projectId)
                .assessmentDate(OffsetDateTime.now())
                .status(AssessmentStatus.IN_PROGRESS)
                .createdAt(OffsetDateTime.now())
                .build();

        AssessmentResult saved = assessmentResultRepository.save(ar);
        Long generatedAssessmentId = saved.getId();

        // 拉取候选并执行计算+入库，使用数据库生成的 assessmentId
        List<Scored<Indicator>> indicators = fetchTopIndicators(behavior, 3);
        List<Scored<Regulation>> regulations = fetchTopRegulations(behavior, 5);

        return computeAndPersistWithIds(behavior, indicators, regulations, projectId, generatedAssessmentId);
    }

    // 简化后：使用带相似度的候选项（Scored<T>），直接根据相似度与适用性判断是否影响指标
    public static class Scored<T> {
        private final T item;
        private final double sim;
        public Scored(T item, double sim) { this.item = item; this.sim = sim; }
        public T getItem() { return item; }
        public double getSim() { return sim; }
    }

    // 内部类：存储法规得分及其权重信息
    private static class RegulationScore {
        final String regulationId;
        final double score;
        final double hierarchyWeight;
        final double timelinessWeight;
        final double similarityWeight;

        RegulationScore(String regulationId, double score, double hierarchyWeight, double timelinessWeight, double similarityWeight) {
            this.regulationId = regulationId;
            this.score = score;
            this.hierarchyWeight = hierarchyWeight;
            this.timelinessWeight = timelinessWeight;
            this.similarityWeight = similarityWeight;
        }
    }

    // 改名：保留原 compute-only 方法（不入库），接收带分数的候选列表
    public MappingResult computeMappingFromCandidates(Behavior behavior,
                                                      List<Scored<Indicator>> indicators,
                                                      List<Scored<Regulation>> regulations) {

        List<String> warnings = new ArrayList<>();

        // 存储每个指标下所有法规的详细信息：(法规得分, 层级权重, 相似度权重)
        Map<String, List<RegulationScore>> indicatorRegScores = new HashMap<>();
        Map<String, List<String>> indicatorToRegs = new HashMap<>();

        // 初始化候选指标
        for (Scored<Indicator> s : indicators) {
            Indicator ind = s.getItem();
            if (ind != null && ind.getId() != null) {
                indicatorRegScores.put(ind.getId(), new ArrayList<>());
            }
        }

        // 对于每个法规候选，根据相似度和适用性计算对指标的影响
        for (Scored<Regulation> sreg : regulations) {
            Regulation reg = sreg.getItem();
            if (reg == null) continue;

            // 获取向量和标签数据用于本地相似度计算
            float[] behaviorVec = (behavior != null) ? behavior.getDescription_vector() : null;

            float[] regVec = reg.getFullTextVector();
            List<String> behaviorTags = (behavior != null) ? behavior.getTags() : null;
            List<String> regTags = reg.getTags();

            boolean hasBehaviorVec = (behaviorVec != null && behaviorVec.length > 0);
            boolean hasRegVec = (regVec != null && regVec.length > 0);
            boolean hasBehaviorTags = (behaviorTags != null && !behaviorTags.isEmpty());
            boolean hasRegTags = (regTags != null && !regTags.isEmpty());

            int behaviorVecDim = hasBehaviorVec ? behaviorVec.length : 0;
            int regVecDim = hasRegVec ? regVec.length : 0;
            int behaviorTagCount = hasBehaviorTags ? behaviorTags.size() : 0;
            int regTagCount = hasRegTags ? regTags.size() : 0;

            // 获取向量前几个值用于验证
            String behaviorVecPreview = getVectorPreview(behaviorVec, 5);
            String regVecPreview = getVectorPreview(regVec, 5);

            log.info("[Vector/Tags Check] regId={}, behaviorVec={}(dim={}), regVec={}(dim={}), behaviorTags={}(count={}), regTags={}(count={})",
                    reg.getId(), hasBehaviorVec, behaviorVecDim, hasRegVec, regVecDim,
                    hasBehaviorTags, behaviorTagCount, hasRegTags, regTagCount);

            log.info("[Vector Preview] regId={}, behaviorVecFirst5={}, regVecFirst5={}",
                    reg.getId(), behaviorVecPreview, regVecPreview);

            if (hasBehaviorTags && hasRegTags) {
                log.info("[Tags Detail] regId={}, behaviorTags={}, regTags={}",
                        reg.getId(), behaviorTags, regTags);
            }

            // 获取ES分数（如果有的话）
            double esScore = sreg.getSim();

            // 使用向量+标签计算行为与法规的相似度
            double computedSim = SimilarityCalculator.scoreBehaviorToTargetDefault(
                    behaviorVec, regVec, behaviorTags, regTags);

            // 最终相似度：优先使用本地计算值，如果本地计算失败则使用ES分数作为保底
            double behaviorRegSim = computedSim;
            boolean usedFallback = false;
            if (computedSim <= 0.0 && esScore > 0.0) {
                behaviorRegSim = esScore;
                usedFallback = true;
            }

            log.info("[Behavior->Regulation Similarity] regId={}, esScore={}, computedSim={}, finalSim={}, threshold={}, passed={}, usedFallback={}",
                    reg.getId(), esScore, computedSim, behaviorRegSim, REG_APPLICABILITY_THRESHOLD,
                    behaviorRegSim >= REG_APPLICABILITY_THRESHOLD, usedFallback);

            // 检查是否通过相似度阈值
            if (behaviorRegSim < REG_APPLICABILITY_THRESHOLD) {
                log.info("[Regulation Skipped] regId={} did not meet threshold (sim={} < threshold={})",
                        reg.getId(), behaviorRegSim, REG_APPLICABILITY_THRESHOLD);
                continue;
            }

            // 对当前法规进行定性计算（定量计算暂不实现）
            double qualitativeScore = computeRegulationScore(behavior, reg);

            // 计算法规层级权重和时效性权重
            double hierarchyWeight = RegWeightCalculator.getHierarchyWeight(reg);
            double timelinessWeight = RegWeightCalculator.getTimelinessWeight(reg,behavior);

            if (log.isDebugEnabled()) {
                log.debug("[computeMappingFromCandidates] regulation calculated: regId={}, direction={}, dimension={}, applicableSubject={}, behaviorStatus={}, qualitativeScore={}, hierarchyWeight={}, timelinessWeight={}",
                        reg.getId(), reg.getDirection(), reg.getDimension(), reg.getApplicableSubject(), behavior != null ? behavior.getStatus() : null, qualitativeScore, hierarchyWeight, timelinessWeight);
            }

            // 遍历所有指标，判断当前法规是否影响该指标
            for (Scored<Indicator> sind : indicators) {
                Indicator ind = sind.getItem();
                if (ind == null || ind.getId() == null) continue;

                // 获取指标向量预览
                float[] indVec = ind.getNameVector();
                String indVecPreview = getVectorPreview(indVec, 5);
                boolean hasIndVec = (indVec != null && indVec.length > 0);
                int indVecDim = hasIndVec ? indVec.length : 0;

                log.info("[Reg->Ind Vector Check] regId={}, indicatorId={}, regVec={}(dim={}), indVec={}(dim={}), regVecPreview={}, indVecPreview={}",
                        reg.getId(), ind.getId(), hasRegVec, regVecDim, hasIndVec, indVecDim, regVecPreview, indVecPreview);

                log.info("[Reg->Ind Tags Check] regId={}, indicatorId={}, regTags={}, indTags={}, regIndustry={}, indIndustry={}",
                        reg.getId(), ind.getId(), reg.getTags(), ind.getTags(), reg.getIndustry(), ind.getIndustry());

                // 计算法规与指标的相似度
                double regIndSim = SimilarityCalculator.scoreRegToIndicatorDefault(
                        regVec, indVec,
                        reg.getTags(), ind.getTags(),
                        reg.getIndustry(), ind.getIndustry()
                );

                // 计算影响力：法规与指标相似度 * 行为与法规相似度
                double influence = regIndSim * behaviorRegSim;

                log.info("[Regulation->Indicator Influence] regId={}, indicatorId={}, regIndSim={}, behaviorRegSim={}, influence={}, threshold={}, passed={}",
                        reg.getId(), ind.getId(), regIndSim, behaviorRegSim, influence, REG_TO_INDICATOR_THRESHOLD, influence >= REG_TO_INDICATOR_THRESHOLD);

                // 检查是否通过影响力阈值
                if (influence < REG_TO_INDICATOR_THRESHOLD) {
                    log.info("[Regulation-Indicator Link Skipped] regId={} -> indicatorId={} did not meet threshold (influence={} < threshold={})",
                            reg.getId(), ind.getId(), influence, REG_TO_INDICATOR_THRESHOLD);
                    continue;
                }

                // 将法规详细信息（得分、层级权重、时效性权重、相似度权重）加入到指标的列表中
                indicatorRegScores.get(ind.getId()).add(
                    new RegulationScore(reg.getId(), qualitativeScore, hierarchyWeight, timelinessWeight, behaviorRegSim)
                );
                indicatorToRegs.computeIfAbsent(ind.getId(), k -> new ArrayList<>()).add(reg.getId());

                log.info("[Regulation Applied to Indicator] regId={}, indicatorId={}, qualitativeScore={}, hierarchyWeight={}, timelinessWeight={}, similarityWeight={}",
                        reg.getId(), ind.getId(), qualitativeScore, hierarchyWeight, timelinessWeight, behaviorRegSim);

                if (log.isDebugEnabled()) {
                    log.debug("[computeMappingFromCandidates] regulation applies to indicator: regId={}, indicatorId={}, regIndSim={}, behaviorRegSim={}, influence={}, qualitativeScore={}, hierarchyWeight={}, timelinessWeight={}",
                            reg.getId(), ind.getId(), regIndSim, behaviorRegSim, influence, qualitativeScore, hierarchyWeight, timelinessWeight);
                }
            }
        }

        // 计算每个指标的最终得分：按照 (层级权重 + 相似度) 的加权平均
        Map<String, Double> finalScores = new HashMap<>();
        for (Scored<Indicator> sind : indicators) {
            Indicator ind = sind.getItem();
            if (ind == null || ind.getId() == null) continue;

            List<RegulationScore> regScoreList = indicatorRegScores.get(ind.getId());

            if (regScoreList == null || regScoreList.isEmpty()) {
                // 对未被法规影响的指标做兜底
                double fallback = getFallback(behavior, ind);
                double clampedFallback = FallbackCalculator.clamp01(fallback);
                finalScores.put(ind.getId(), clampedFallback);

                log.warn("[FALLBACK TRIGGERED] indicatorId={}, indicatorName={}, fallbackScore={}, clampedScore={}, reason=NO_REGULATIONS_MATCHED",
                        ind.getId(), ind.getName(), fallback, clampedFallback);

                if (fallback > 0) {
                    indicatorToRegs.computeIfAbsent(ind.getId(), k -> new ArrayList<>()).add("FALLBACK");
                }
                if (fallback == 0) {
                    warnings.add("Indicator " + ind.getId() + " has zero fallback score");
                    log.warn("[FALLBACK WARNING] indicatorId={} has zero fallback score", ind.getId());
                }

            }

            else {
                // 计算加权平均：权重 = (层级权重 + 时效性权重 + 相似度权重) / 3
                double weightedSum = 0.0;
                double totalWeight = 0.0;

                log.info("[Indicator Score Calculation START] indicatorId={}, indicatorName={}, matchedRegulationsCount={}",
                        ind.getId(), ind.getName(), regScoreList.size());

                for (RegulationScore rs : regScoreList) {
                    // 权重 = (层级权重 + 时效性权重 + 相似度权重) / 3
                    double weight = (rs.hierarchyWeight + rs.timelinessWeight + rs.similarityWeight) / 3.0;
                    weightedSum += rs.score * weight;
                    totalWeight += weight;

                    log.info("[Regulation Contribution] indicatorId={}, regId={}, score={}, hierarchyWeight={}, timelinessWeight={}, similarityWeight={}, combinedWeight={}, weightedContribution={}",
                            ind.getId(), rs.regulationId, rs.score, rs.hierarchyWeight, rs.timelinessWeight, rs.similarityWeight, weight, rs.score * weight);

                    if (log.isDebugEnabled()) {
                        log.debug("[computeMappingFromCandidates] regulation weighted contribution: indicatorId={}, regId={}, score={}, hierarchyWeight={}, timelinessWeight={}, similarityWeight={}, weight={}, contribution={}",
                                ind.getId(), rs.regulationId, rs.score, rs.hierarchyWeight, rs.timelinessWeight, rs.similarityWeight, weight, rs.score * weight);
                    }
                }

                double weightedAvgScore = totalWeight > 0 ? weightedSum / totalWeight : 0.0;
                double clampedScore = FallbackCalculator.clamp01(weightedAvgScore);
                finalScores.put(ind.getId(), clampedScore);

                log.info("[Indicator Score Calculation COMPLETE] indicatorId={}, indicatorName={}, weightedSum={}, totalWeight={}, rawScore={}, clampedScore={}",
                        ind.getId(), ind.getName(), weightedSum, totalWeight, weightedAvgScore, clampedScore);

                if (log.isDebugEnabled()) {
                    log.debug("[computeMappingFromCandidates] indicator weighted average score: indicatorId={}, regCount={}, weightedAvgScore={}, totalWeight={}",
                            ind.getId(), regScoreList.size(), weightedAvgScore, totalWeight);
                }
            }
        }

        return MappingResult.builder()
                .behaviorId(behavior.getId())
                .indicatorScores(finalScores)
                .indicatorInfluencingRegulations(indicatorToRegs)
                .warnings(warnings)
                .build();
    }

    /**
     * 对单个法规进行定性/定量计算
     * 优先使用定量计算（如果法规和行为都有定量数据），否则使用定性计算
     */
    private double computeRegulationScore(Behavior behavior, Regulation regulation) {
        // 判断是否有定量数据
        boolean hasQuantitativeData = regulation.getQuantitativeIndicator() != null
                && behavior != null
                && behavior.getQuantitative_data() != null;

        if (hasQuantitativeData) {
            // 定量计算：根据法规定量指标和行为定量数据计算
            double quantitativeScore = QuantitativeCalculator.computeQuantitativeScore(
                    regulation.getQuantitativeIndicator(),
                    behavior.getQuantitative_data(),
                    regulation.getDirection(),
                    behavior.getStatus()
            );
            
            return quantitativeScore;
        } else {
            // 定性计算：根据法规方向和行为状态矩阵计算
            double qualitativeScore = QualitativeCalculator.computeQualitativeScore(
                    regulation.getDirection(),
                    behavior != null ? behavior.getStatus() : null
            );

            return qualitativeScore;
        }
    }




    // 改名：计算并入库（projectId 与 assessmentId 由调用方传入）
    public MappingResult computeAndPersistWithIds(Behavior behavior,
                                                  List<Scored<Indicator>> indicators,
                                                  List<Scored<Regulation>> regulations,
                                                  Long projectId,
                                                  Long assessmentId) {
        MappingResult mr = computeMappingFromCandidates(behavior, indicators, regulations);

        // 将 mr.indicatorScores 映射为 IndicatorResult 列表并入库
        try {
            Map<String, Double> scores = mr.getIndicatorScores();
            Map<String, List<String>> indicatorToRegs = mr.getIndicatorInfluencingRegulations();
            List<IndicatorResult> toSave = new ArrayList<>();
            String currentBehaviorId = behavior != null && behavior.getId() != null ? behavior.getId() : null;

            for (Map.Entry<String, Double> e : scores.entrySet()) {
                String indicatorEsId = e.getKey();
                double normalizedScore = e.getValue() == null ? 0.0 : e.getValue();
                // 找到元数据（若能在 candidates 中找到）
                Indicator matched = null;
                for (Scored<Indicator> s : indicators) {
                    if (s.getItem() != null && indicatorEsId.equals(s.getItem().getId())) { matched = s.getItem(); break; }
                }

                // 确保 maxPossible 始终为正数（数据库约束要求）
                double maxPossible = 100.0;  // 默认值
                if (matched != null && matched.getMaxScore() != null && matched.getMaxScore() > 0) {
                    maxPossible = matched.getMaxScore();
                } else {
                    log.warn("[MaxScore Warning] indicatorId={}, indicatorName={}, maxScore from ES={}, using default={}",
                            indicatorEsId,
                            matched != null ? matched.getName() : "UNKNOWN",
                            matched != null ? matched.getMaxScore() : "null",
                            maxPossible);
                }

                double absoluteScore = normalizedScore * maxPossible;

                log.info("[Score Calculation] indicatorId={}, normalizedScore={}, maxPossible={}, absoluteScore={}",
                        indicatorEsId, normalizedScore, maxPossible, absoluteScore);

                // 查找是否存在相同 assessmentId 和 indicatorEsId 的记录
                Optional<IndicatorResult> existingOpt = indicatorResultRepository.findByAssessmentIdAndIndicatorEsId(assessmentId, indicatorEsId);

                if (existingOpt.isPresent() && currentBehaviorId != null) {
                    IndicatorResult existing = existingOpt.get();
                    String[] existingBehaviorIds = existing.getMatchedBehaviorsIds();

                    // 检查当前 behaviorId 是否已经在列表中
                    boolean alreadyContains = existingBehaviorIds != null && Arrays.asList(existingBehaviorIds).contains(currentBehaviorId);

                    if (alreadyContains) {
                        // 如果已包含当前 behaviorId，则更新记录：平均分数，合并 behavior IDs
                        double existingScore = existing.getCalculatedScore().doubleValue();
                        double averagedScore = (existingScore*existingBehaviorIds.length + absoluteScore) / (existingScore+existingBehaviorIds.length+1);

                        // 合并 behavior IDs（去重）
                        Set<String> mergedBehaviorIds = new HashSet<>(Arrays.asList(existingBehaviorIds));
                        mergedBehaviorIds.add(currentBehaviorId);

                        existing.setCalculatedScore(BigDecimal.valueOf(averagedScore));
                        existing.setMatchedBehaviorsIds(mergedBehaviorIds.toArray(new String[0]));
                        existing.setCalculatedAt(OffsetDateTime.now());

                        // 更新 calculation_details
                        ObjectNode details = buildCalculationDetails(averagedScore / maxPossible, indicatorToRegs != null ? indicatorToRegs.get(indicatorEsId) : null);
                        details.put("absoluteScore", averagedScore);
                        details.put("averagedFrom", "existing_and_new");
                        existing.setCalculationDetails(details.toString());

                        toSave.add(existing);

                        log.info("[computeAndPersistWithIds] Updated existing IndicatorResult: assessmentId={}, indicatorEsId={}, oldScore={}, newScore={}, averagedScore={}",
                                assessmentId, indicatorEsId, existingScore, absoluteScore, averagedScore);
                        continue;
                    }
                }

                // 否则创建新记录
                ObjectNode details = buildCalculationDetails(normalizedScore, indicatorToRegs != null ? indicatorToRegs.get(indicatorEsId) : null);
                details.put("absoluteScore", absoluteScore);

                IndicatorResult ir = IndicatorResult.builder()
                        .projectId(projectId)
                        .assessmentId(assessmentId)
                        .indicatorEsId(indicatorEsId)
                        .indicatorName(matched != null ? matched.getName() : (indicatorEsId == null ? "" : indicatorEsId))
                        .indicatorLevel(matched != null && matched.getIndicatorLevel() != null ? matched.getIndicatorLevel() : 0)
                        .dimension(matched != null ? matched.getDimension() : null)
                        .type(matched != null ? matched.getType() : null)
                        .calculatedScore(BigDecimal.valueOf(absoluteScore))
                        .maxPossibleScore(BigDecimal.valueOf(maxPossible))
                        .usedCalculationRuleType("auto")
                        .calculationDetails(details.toString())
                        .matchedBehaviorsIds(currentBehaviorId != null ? new String[]{currentBehaviorId} : new String[0])
                        .riskTriggered(false)
                        .riskStatus(IndicatorRiskStatus.fromCode("NOT_EVALUATED"))
                        .calculatedAt(OffsetDateTime.now())
                        .createdAt(OffsetDateTime.now())
                        .build();
                toSave.add(ir);
            }
            indicatorResultRepository.saveAll(toSave);
        } catch (Exception ex) {
            // 捕获并记录异常，避免影响主流程
            log.error("Failed to save IndicatorResult list", ex);
        }

        return mr;
    }

    private static double getFallback(Behavior behavior, Indicator ind) {
        double fallback;
        if (ind.getMaxScore() != null && behavior.getQuantitative_data() != null) {
            double baseline = ind.getMaxScore();
            double deviation = Math.abs(baseline - behavior.getQuantitative_data());
            double ratio = deviation / (baseline == 0.0 ? 1.0 : baseline);
            fallback = Math.max(0.0, 1.0 - ratio);
        } else {
            fallback = FallbackCalculator.qualitativeFallback(behavior.getStatus());
        }
        return fallback;
    }

    // 新增：从 ES 拉取候选指标：简单的文本多字段匹配，返回 ES hit score 作为相似度
    public List<Scored<Indicator>> fetchTopIndicators(Behavior behavior, int candidateSize) {
        try {
            String text = (behavior.getDescription() == null ? "" : behavior.getDescription())
                    + " " + (behavior.getTags() == null ? "" : String.join(" ", behavior.getTags()));

            log.info("[Fetching Indicator Candidates] behaviorId={}, candidateSize={}, queryText='{}'",
                    behavior.getId(), candidateSize, text);

            SearchResponse<Indicator> resp = esClient.search(s -> s
                            .index(indicatorIndex)
                            .size(candidateSize)
                            .query(q -> q.multiMatch(mm -> mm
                                    .fields(Arrays.asList("name", "description", "tags"))
                                    .query(text)
                            ))
                            // 移除source过滤器，获取完整的指标数据（包括向量）
                    , Indicator.class);

            List<Scored<Indicator>> out = new ArrayList<>();
            if (resp != null && resp.hits() != null) {
                for (Hit<Indicator> h : resp.hits().hits()) {
                    Indicator ind = h.source();
                    double score = h.score() == null ? 0.0 : h.score();
                    out.add(new Scored<>(ind, score));
                    log.info("[Indicator Candidate] indicatorId={}, indicatorName={}, hasVector={}, esScore={}",
                            ind.getId(), ind.getName(),
                            (ind.getNameVector() != null && ind.getNameVector().length > 0), score);
                }
            }
            log.info("[Indicator Candidates Fetched] totalCount={}", out.size());
            return out;
        } catch (Exception ex) {
            log.error("[Fetching Indicator Candidates Failed] behaviorId={}, error={}", behavior.getId(), ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    // 新增：从 ES 拉取候选法规
    public List<Scored<Regulation>> fetchTopRegulations(Behavior behavior, int candidateSize) {
        try {
            String text = (behavior.getDescription() == null ? "" : behavior.getDescription())
                    + " " + (behavior.getTags() == null ? "" : String.join(" ", behavior.getTags()));

            log.info("[Fetching Regulation Candidates] behaviorId={}, candidateSize={}, queryText='{}'",
                    behavior.getId(), candidateSize, text);

            SearchResponse<Regulation> resp = esClient.search(s -> s
                            .index(regulationIndex)
                            .size(candidateSize)
                            .query(q -> q.multiMatch(mm -> mm
                                    .fields(Arrays.asList("title", "full_text", "tags"))
                                    .query(text)
                            ))
                            // 移除source过滤器，获取完整的法规数据（包括向量）
                    , Regulation.class);

            List<Scored<Regulation>> out = new ArrayList<>();
            if (resp != null && resp.hits() != null) {
                for (Hit<Regulation> h : resp.hits().hits()) {
                    Regulation reg = h.source();
                    double score = h.score() == null ? 0.0 : h.score();
                    out.add(new Scored<>(reg, score));
                    log.info("[Regulation Candidate] regulationId={}, regulationName={}, regulationDimension={}, regulationType={}, hasVector={}, regulationTags={}",
                            reg.getId(), reg.getName(), reg.getDimension(), reg.getType(),
                            (reg.getFullTextVector() != null && reg.getFullTextVector().length > 0), reg.getTags());
                }
            }
            log.info("[Regulation Candidates Fetched] totalCount={}", out.size());
            return out;
        } catch (Exception ex) {
            log.error("[Fetching Regulation Candidates Failed] behaviorId={}, error={}", behavior.getId(), ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    // 新增用于构建 calculation_details 的简单 JSON
    private ObjectNode buildCalculationDetails(double score, List<String> influencingRegs) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("score", score);
        if (influencingRegs != null) {
            node.putPOJO("influencingRegulations", influencingRegs);
        } else {
            node.putPOJO("influencingRegulations", Collections.emptyList());
        }
        // 可在此添加更多中间过程信息
        return node;
    }

    /**
     * 获取向量的前N个值的字符串表示，用于日志输出
     * @param vector 向量数组
     * @param count 要显示的元素个数
     * @return 格式化的字符串，如 "[0.123, -0.456, 0.789, ...]"
     */
    private String getVectorPreview(float[] vector, int count) {
        if (vector == null) {
            return "null";
        }
        if (vector.length == 0) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        int limit = Math.min(count, vector.length);
        for (int i = 0; i < limit; i++) {
            sb.append(String.format("%.4f", vector[i]));
            if (i < limit - 1) {
                sb.append(", ");
            }
        }
        if (vector.length > count) {
            sb.append(", ...");
        }
        sb.append("]");
        return sb.toString();
    }
}
