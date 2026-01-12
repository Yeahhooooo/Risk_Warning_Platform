package com.riskwarning.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskwarning.common.config.ElasticSearchConfig;
import com.riskwarning.common.constants.Constants;
import com.riskwarning.common.constants.RedisKey;
import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.common.enums.indicator.IndicatorRiskStatus;
import com.riskwarning.common.exception.BusinessException;
import com.riskwarning.common.message.AssessmentCompletedEventMessage;
import com.riskwarning.common.po.behavior.Behavior;
import com.riskwarning.common.po.indicator.Indicator;
import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.po.indicator.IndicatorResultDetail;
import com.riskwarning.common.po.regulation.Regulation;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.po.risk.RelatedBehavior;
import com.riskwarning.common.po.risk.RelatedIndicator;
import com.riskwarning.common.po.risk.RelatedRegulation;
import com.riskwarning.common.utils.KafkaUtils;
import com.riskwarning.common.utils.RedisUtil;
import com.riskwarning.common.utils.StringUtils;
import com.riskwarning.processing.dto.MappingResult;
import com.riskwarning.processing.repository.AssessmentRepository;
import com.riskwarning.processing.repository.IndicatorResultRepository;
import com.riskwarning.processing.util.behavior.FallbackCalculator;
import com.riskwarning.processing.util.behavior.QualitativeCalculator;
import com.riskwarning.processing.util.behavior.QuantitativeCalculator;
import com.riskwarning.processing.util.behavior.RegWeightCalculator;
import com.riskwarning.processing.util.behavior.SimilarityCalculator;
import com.fasterxml.jackson.databind.node.ObjectNode;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class BehaviorProcessingService {

    private static final double REG_APPLICABILITY_THRESHOLD = 0.15;

    private static final double REG_TO_INDICATOR_THRESHOLD = 0.2;

    private static final String BEHAVIOR_VECTOR_FIELD = "description_vector";

    private static final String INDICATOR_VECTOR_FIELD = "name_vector";

    private static final String REGULATION_VECTOR_FIELD = "full_text_vector";

    private static final Integer CANDIDATE_FETCH_SIZE = 200;


    // 新增：注入 ElasticsearchClient 与索引名配置
    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private IndicatorResultRepository indicatorResultRepository;

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    @Qualifier(value = "BehaviorProcessTaskThreadPool")
    private ThreadPoolTaskExecutor behaviorThreadPoolExecutor;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private KafkaUtils kafkaUtils;

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

    /**
     * 内部类：用于存储单个 behavior 的计算结果
     * ✅ 只存储 DTO/基本类型，不存储 JPA Entity
     */
    private static class BehaviorCalculationResult {
        final String behaviorId;
        final Map<String, IndicatorMetadataDTO> indicatorMetadata;
        final MappingResult result;

        BehaviorCalculationResult(String behaviorId,
                                  Map<String, IndicatorMetadataDTO> indicatorMetadata,
                                  MappingResult result) {
            this.behaviorId = behaviorId;
            this.indicatorMetadata = indicatorMetadata;
            this.result = result;
        }
    }

    /**
     * DTO：指标元数据
     * ✅ 纯数据类，不包含 JPA Entity
     */
    private static class IndicatorMetadataDTO {
        final String indicatorEsId;
        final String name;
        final Integer indicatorLevel;
        final String dimension;
        final String type;
        final Double maxScore;

        IndicatorMetadataDTO(String indicatorEsId, String name, Integer indicatorLevel,
                             String dimension, String type, Double maxScore) {
            this.indicatorEsId = indicatorEsId;
            this.name = name;
            this.indicatorLevel = indicatorLevel;
            this.dimension = dimension;
            this.type = type;
            this.maxScore = maxScore;
        }
    }

    /**
     * 新方法：传入 projectId 和 assessmentId，从 ES 获取该项目的 behaviors，
     * 使用传入的 assessmentId 进行评估，不再创建新的 assessment
     *
     * 临时修改：由于ES中behaviors的projectId为null，改为随机获取5个行为并设置projectId为7
     *
     * 注意：移除 @Transactional，因为此方法可能在线程池中调用
     *
     * @param projectId 项目ID
     * @param assessmentId 评估ID（由调用方创建并传入）
     */
    public void processProjectBehaviors(Long userId, Long projectId, Long assessmentId) {
        if (userId == null || projectId == null || assessmentId == null) {
            throw new BusinessException("User ID, Project ID, and Assessment ID must be provided for behavior processing.");
        }
        updateAssessmentStatus(assessmentId, AssessmentStatusEnum.ASSESSING);
        log.info("[Process Project START] projectId={}, assessmentId={} (传入)", projectId, assessmentId);

        // 1. 从 ES 随机获取 behaviors
        //TODO:待修改
        List<Behavior> behaviors = fetchRandomBehaviors(5, projectId);

        if (behaviors.isEmpty()) {
            throw new IllegalArgumentException("No behaviors found for projectId: " + projectId);
        }

        // 2. 使用传入的 assessmentId，不再创建新的 assessment
        log.info("[使用传入的 Assessment] assessmentId={}, projectId={}, behaviorCount={}",
                assessmentId, projectId, behaviors.size());

        for (Behavior behavior : behaviors) {
            behaviorThreadPoolExecutor.execute(() -> {
                try {
                    // ✅ 诊断：检查计算阶段是否有事务
                    log.debug("[Before Calculation] TX active = {}, thread={}",
                            TransactionSynchronizationManager.isActualTransactionActive(),
                            Thread.currentThread().getName());

                    // 并发进行计算：获取候选指标和法规
                    List<Scored<Indicator>> indicators = fetchTopIndicators(behavior, 6);
                    List<Scored<Regulation>> regulations = fetchTopRegulations(behavior, 10);

                    // ✅ 诊断：检查获取候选后是否有事务
                    log.debug("[After Fetch Candidates] TX active = {}, thread={}",
                            TransactionSynchronizationManager.isActualTransactionActive(),
                            Thread.currentThread().getName());

                    // 只计算，不保存
                    MappingResult result = computeMappingFromCandidates(behavior, indicators, regulations);

                    // ✅ 提取元数据为 DTO，不传递 JPA Entity
                    String behaviorId = behavior.getId();
                    Map<String, IndicatorMetadataDTO> indicatorMetadata = new HashMap<>();
                    for (Scored<Indicator> s : indicators) {
                        if (s.getItem() != null && s.getItem().getId() != null) {
                            Indicator ind = s.getItem();
                            indicatorMetadata.put(ind.getId(), new IndicatorMetadataDTO(
                                ind.getId(),
                                ind.getName(),
                                ind.getIndicatorLevel(),
                                ind.getDimension(),
                                ind.getType(),
                                ind.getMaxScore()
                            ));
                        }
                    }
                    // ✅ 诊断：检查提取元数据后是否有事务
                    log.debug("[After Extract Metadata] TX active = {}, thread={}",
                            TransactionSynchronizationManager.isActualTransactionActive(),
                            Thread.currentThread().getName());
                    // 保存计算结果
                    BehaviorCalculationResult calcResult = new BehaviorCalculationResult(
                            behaviorId,
                            indicatorMetadata,
                            result
                    );
                    saveIndicatorResult(calcResult, projectId, assessmentId);
                } catch (Exception e) {
                    log.error("[Behavior Processing Failed] behaviorId={}, error={}", behavior.getId(), e.getMessage());
                } finally {
                    // redis原子性计数
                    long alCount = redisUtil.incr(String.format(RedisKey.REDIS_KEY_BEHAVIOR_PROCESSING_COUNT, projectId), 1);
                    // TODO: 修改为检测count == es内该项目行为数目
                    if(alCount == 5){
                        completeAssessmentIfNeeded(userId, projectId, assessmentId);
                    }
                }
            });
        }
    }

    private void updateAssessmentStatus(Long assessmentId, AssessmentStatusEnum assessmentStatus) {
        Optional<Assessment> opt = assessmentRepository.findById(assessmentId);
        if (opt.isPresent()) {
            Assessment ar = opt.get();
            ar.setStatus(assessmentStatus);
            assessmentRepository.save(ar);
            log.info("[Assessment Status Updated] assessmentId={}, status={}", assessmentId, assessmentStatus);
        } else {
            log.warn("[Assessment Not Found] assessmentId={}", assessmentId);
            throw new RuntimeException("[Assessment Not Found] assessmentId=" + assessmentId);
        }
    }

    private void saveIndicatorResult(BehaviorCalculationResult calcResult, Long projectId, Long assessmentId) {
        String behaviorId = calcResult.behaviorId;
        Map<String, IndicatorMetadataDTO> indicatorMetadata = calcResult.indicatorMetadata;
        MappingResult mr = calcResult.result;


        if (mr == null || mr.getRelatedIndicators() == null || mr.getRelatedIndicators().isEmpty()) {
            log.debug("[No Scores to Save] behaviorId={}", behaviorId);
            return;
        }

        Map<String, RelatedIndicator> relatedIndicators = mr.getRelatedIndicators();

        for (Map.Entry<String, RelatedIndicator> e : relatedIndicators.entrySet()) {
            String indicatorEsId = e.getKey();
            RelatedIndicator ri = e.getValue();
            double normalizedScore = ri.getScore();

            IndicatorMetadataDTO metadata = indicatorMetadata.get(indicatorEsId);
            double maxPossible = 100.0;
            if (metadata != null && metadata.maxScore != null && metadata.maxScore > 0) {
                maxPossible = metadata.maxScore;
            }
            double absoluteScore = normalizedScore * maxPossible;

            // 使用 JPQL 查询
            Optional<IndicatorResult> alIndicatorResult = indicatorResultRepository.findByAssessmentIdAndIndicatorEsId(assessmentId, indicatorEsId);

            if (alIndicatorResult.isPresent()) {
                IndicatorResult existing = alIndicatorResult.get();
                IndicatorResultDetail indicatorResultDetail = existing.getCalculationDetails();

                boolean alreadyContains = false;
                for(RelatedIndicator relatedIndicator : indicatorResultDetail.getRelatedIndicators()) {
                    if(relatedIndicator.getIndicatorId().equals(behaviorId)) {
                        alreadyContains = true;
                        break;
                    }
                }
                if (!alreadyContains) {
                    double existingScore = existing.getCalculatedScore();
                    int existingCount = indicatorResultDetail.getRelatedIndicators().size();
                    double averagedScore = (existingScore * existingCount + absoluteScore) / (existingCount + 1);
                    existing.setCalculatedScore(averagedScore);
                    existing.setCalculatedAt(LocalDateTime.now());
                    indicatorResultDetail.getRelatedIndicators().add(ri);
                    existing.setCalculationDetails(indicatorResultDetail);
                    indicatorResultRepository.save(existing);

                    // TODO: 这里需要通过CAS控制并发更新丢失问题
                }
            } else {
                IndicatorResult ir = IndicatorResult.builder()
                        .projectId(projectId)
                        .assessmentId(assessmentId)
                        .indicatorEsId(indicatorEsId)
                        .indicatorName(metadata != null ? metadata.name : indicatorEsId)
                        .indicatorLevel(metadata != null && metadata.indicatorLevel != null ? metadata.indicatorLevel : 0)
                        .dimension(metadata != null ? metadata.dimension : null)
                        .type(metadata != null ? metadata.type : null)
                        .calculatedScore(absoluteScore)
                        .maxPossibleScore(maxPossible)
                        .usedCalculationRuleType("auto")
                        .calculationDetails(IndicatorResultDetail.builder()
                                .relatedIndicators(Collections.singletonList(ri))
                                .build())
                        .riskTriggered(false)
                        .riskStatus(IndicatorRiskStatus.fromCode("NOT_EVALUATED"))
                        .calculatedAt(LocalDateTime.now())
                        .createdAt(LocalDateTime.now())
                        .build();
                indicatorResultRepository.save(ir);
            }
        }
    }

    private void completeAssessmentIfNeeded(Long userId, Long projectId, Long assessmentId) {
        AssessmentCompletedEventMessage assessmentCompletedEventMessage = new AssessmentCompletedEventMessage(
                StringUtils.generateMessageId(),
                String.valueOf(System.currentTimeMillis()),
                StringUtils.generateTraceId(),
                userId,
                projectId,
                assessmentId
        );
        redisUtil.del(String.format(RedisKey.REDIS_KEY_BEHAVIOR_PROCESSING_COUNT, projectId));
        kafkaUtils.sendMessage(assessmentCompletedEventMessage);
    }

    // 改名：保留原 compute-only 方法（不入库），接收带分数的候选列表
    public MappingResult computeMappingFromCandidates(Behavior behavior,
                                                      List<Scored<Indicator>> indicators,
                                                      List<Scored<Regulation>> regulations) {

        List<String> warnings = new ArrayList<>();

        // 存储每个指标下所有法规的详细信息：(法规得分, 层级权重, 相似度权重)
        Map<String, List<RegulationScore>> indicatorRegScores = new HashMap<>();
        Map<String, RelatedIndicator> indicatorResults = new HashMap<>();

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
            List<Float> behaviorVec = (behavior != null) ? behavior.getDescriptionVector() : null;

            List<Float> regVec = reg.getFullTextVector();
            List<String> behaviorTags = (behavior != null) ? behavior.getTags() : null;
            List<String> regTags = reg.getTags();

            boolean hasBehaviorVec = (behaviorVec != null && !behaviorVec.isEmpty());

            // 获取ES分数（如果有的话）
            double esScore = sreg.getSim();

            // 使用向量+标签计算行为与法规的相似度
            double computedSim = SimilarityCalculator.scoreBehaviorToTargetDefault(
                    behaviorVec, regVec, behaviorTags, regTags);

            // 最终相似度：优先使用本地计算值，如果本地计算失败则使用ES分数作为保底
            double behaviorRegSim = computedSim;
            if (computedSim <= 0.0 && esScore > 0.0) {
                behaviorRegSim = esScore;
            }

            // 检查是否通过相似度阈值
            if (behaviorRegSim < REG_APPLICABILITY_THRESHOLD) {
                continue;
            }

            // 对当前法规进行定性计算
            double qualitativeScore = computeRegulationScore(behavior, reg);

            // 计算法规层级权重和时效性权重
            double hierarchyWeight = RegWeightCalculator.getHierarchyWeight(reg);
            double timelinessWeight = RegWeightCalculator.getTimelinessWeight(reg,behavior);

            // 遍历所有指标，判断当前法规是否影响该指标
            for (Scored<Indicator> sind : indicators) {
                Indicator ind = sind.getItem();
                if (ind == null || ind.getId() == null) continue;

                // 获取指标向量
                List<Float> indVec = ind.getNameVector();

                // 计算法规与指标的相似度
                double regIndSim = SimilarityCalculator.scoreRegToIndicatorDefault(
                        regVec, indVec,
                        reg.getTags(), ind.getTags(),
                        reg.getIndustry(), ind.getIndustry()
                );

                // 计算影响力：法规与指标相似度 * 行为与法规相似度
                double influence = regIndSim * behaviorRegSim;

                // 检查是否通过影响力阈值
                if (influence < REG_TO_INDICATOR_THRESHOLD) {
                    continue;
                }

                // 将法规详细信息加入到指标的列表中
                indicatorRegScores.get(ind.getId()).add(
                    new RegulationScore(reg.getId(), qualitativeScore, hierarchyWeight, timelinessWeight, behaviorRegSim)
                );
                RelatedIndicator indicatorResult = indicatorResults.getOrDefault(ind.getId(),
                        RelatedIndicator.builder()
                                .indicatorId(ind.getId())
                                .indicatorName(ind.getName())
                                .score(0.0)
                                .maxScore(ind.getMaxScore())
                                .relatedBehaviors(new ArrayList<>())
                                .build());

                // 因为这里一直是同一个behavior，只需要添加regulation即可
                if(indicatorResult.getRelatedBehaviors().isEmpty()){
                    indicatorResult.getRelatedBehaviors().add(
                            RelatedBehavior.builder()
                                    .projectId(behavior.getProjectId())
                                    .description(behavior.getDescription())
                                    .relatedRegulations(Collections.singletonList(
                                            RelatedRegulation.builder()
                                                    .regulationId(reg.getId())
                                                    .regulationName(reg.getName())
                                                    // todo: 填充相关法律更多字段
                                                    .violationType("")
                                                    .complianceRequirement("")
                                                    .build()
                                    ))
                                    .build()
                    );
                } else {
                    indicatorResult.getRelatedBehaviors().get(0).getRelatedRegulations().add(
                            RelatedRegulation.builder()
                                    .regulationId(reg.getId())
                                    .regulationName(reg.getName())
                                    // todo: 填充相关法律更多字段
                                    .violationType("")
                                    .complianceRequirement("")
                                    .build()
                    );
                }
                indicatorResults.put(ind.getId(), indicatorResult);
            }
        }

        // 计算每个指标的最终得分
        for (Scored<Indicator> sind : indicators) {
            Indicator ind = sind.getItem();
            if (ind == null || ind.getId() == null) continue;

            List<RegulationScore> regScoreList = indicatorRegScores.get(ind.getId());

            if (regScoreList == null || regScoreList.isEmpty()) {
                // 对未被法规影响的指标做兜底
                double fallback = getFallback(behavior, ind);
                double clampedFallback = FallbackCalculator.clamp01(fallback);
                indicatorResults.get(ind.getId()).setScore(clampedFallback);
                if (fallback == 0) {
                    warnings.add("Indicator " + ind.getId() + " has zero fallback score");
                }

            } else {
                // 计算加权平均
                double weightedSum = 0.0;
                double totalWeight = 0.0;

                for (RegulationScore rs : regScoreList) {
                    double weight = (rs.hierarchyWeight + rs.timelinessWeight + rs.similarityWeight) / 3.0;
                    weightedSum += rs.score * weight;
                    totalWeight += weight;
                }

                double weightedAvgScore = totalWeight > 0 ? weightedSum / totalWeight : 0.0;
                double clampedScore = FallbackCalculator.clamp01(weightedAvgScore);
                indicatorResults.get(ind.getId()).setScore(clampedScore);
            }
        }

        return MappingResult.builder()
                .behaviorId(behavior.getId())
                .relatedIndicators(indicatorResults)
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
                && behavior.getQuantitativeData() != null;

        if (hasQuantitativeData) {
            // 定量计算：根据法规定量指标和行为定量数据计算
            double quantitativeScore = QuantitativeCalculator.computeQuantitativeScore(
                    regulation.getQuantitativeIndicator(),
                    behavior.getQuantitativeData(),
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

    private static double getFallback(Behavior behavior, Indicator ind) {
        double fallback;
        if (ind.getMaxScore() != null && behavior.getQuantitativeData() != null) {
            double baseline = ind.getMaxScore();
            double deviation = Math.abs(baseline - behavior.getQuantitativeData());
            double ratio = deviation / (baseline == 0.0 ? 1.0 : baseline);
            fallback = Math.max(0.0, 1.0 - ratio);
        } else {
            fallback = FallbackCalculator.qualitativeFallback(behavior.getStatus());
        }
        return fallback;
    }

    /**
     * 从 ES 中随机获取指定数量的 behaviors 并设置 projectId
     * 临时方案：由于ES中所有behavior的projectId为null，使用随机查询
     */
    private List<Behavior> fetchRandomBehaviors(int count, Long projectId) {
        try {
            int fetchSize = Math.min(count * 5, 100);

            SearchResponse<Behavior> resp = esClient.search(s -> s
                            .index(ElasticSearchConfig.BEHAVIOR_INDEX)
                            .size(fetchSize)
                            .query(q -> q.matchAll(ma -> ma)),
                    Behavior.class
            );

            List<Behavior> allBehaviors = new ArrayList<>();
            if (resp != null && resp.hits() != null && resp.hits().hits() != null) {
                for (Hit<Behavior> hit : resp.hits().hits()) {
                    Behavior behavior = hit.source();
                    if (behavior != null) {
                        allBehaviors.add(behavior);
                    }
                }
            }

            // 在Java中进行随机选择
            List<Behavior> selectedBehaviors = new ArrayList<>();
            if (!allBehaviors.isEmpty()) {
                Collections.shuffle(allBehaviors, new Random(System.currentTimeMillis()));
                int selectCount = Math.min(count, allBehaviors.size());

                for (int i = 0; i < selectCount; i++) {
                    Behavior behavior = allBehaviors.get(i);
                    behavior.setProjectId(projectId);
                    selectedBehaviors.add(behavior);
                }
            }

            return selectedBehaviors;

        } catch (Exception e) {
            log.error("[Fetch Random Behaviors Failed] error={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从 ES 获取指定项目的所有 behaviors
     * 注意：当前ES中projectId都是null，此方法暂时不可用，请使用 fetchRandomBehaviors
     */
    private List<Behavior> fetchBehaviorsByProjectId(Long projectId) {
        try {
            log.info("[Fetching Behaviors from ES] projectId={}, index={}", projectId, ElasticSearchConfig.BEHAVIOR_INDEX);

            SearchResponse<Behavior> resp = esClient.search(s -> s
                            .index(ElasticSearchConfig.BEHAVIOR_INDEX)
                            .size(1000)  // 假设一个项目不会超过1000个behaviors，如需要可以改成scroll
                            .query(q -> q
                                    .term(t -> t
                                            .field("project_id")
                                            .value(projectId)
                                    )
                            )
                            .sort(sort -> sort
                                    .field(f -> f
                                            .field("behavior_date")
                                            .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)
                                    )
                            ),
                    Behavior.class
            );

            List<Behavior> behaviors = new ArrayList<>();
            if (resp != null && resp.hits() != null && resp.hits().hits() != null) {
                for (Hit<Behavior> hit : resp.hits().hits()) {
                    Behavior behavior = hit.source();
                    if (behavior != null) {
                        behaviors.add(behavior);
                        log.debug("[Behavior Fetched] behaviorId={}, description={}",
                                behavior.getId(), behavior.getDescription());
                    }
                }
            }

            log.info("[Behaviors Fetched from ES] projectId={}, count={}", projectId, behaviors.size());
            return behaviors;

        } catch (Exception e) {
            log.error("[Fetching Behaviors Failed] projectId={}, error={}", projectId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // 新增：从 ES 拉取候选指标：简单的文本多字段匹配，返回 ES hit score 作为相似度
    public List<Scored<Indicator>> fetchTopIndicators(Behavior behavior, int candidateSize) {
        try {
            String text = (behavior.getDescription() == null ? "" : behavior.getDescription())
                    + " " + (behavior.getTags() == null ? "" : String.join(" ", behavior.getTags()));

            log.info("[Fetching Indicator Candidates] behaviorId={}, candidateSize={}, queryText='{}'",
                    behavior.getId(), candidateSize, text);

            SearchResponse<Indicator> resp = esClient.search(s -> s
                            .index(ElasticSearchConfig.INDICATOR_INDEX)
                            .size(candidateSize)
                            .knn(k -> k
                                    .field(INDICATOR_VECTOR_FIELD)
                                    .queryVector(
                                            behavior.getDescriptionVector()
                                    )
                                    .k(candidateSize)
                                    .numCandidates(CANDIDATE_FETCH_SIZE)
                            )
                            // 移除source过滤器，获取完整的指标数据（包括向量）
                    , Indicator.class);

            List<Scored<Indicator>> out = new ArrayList<>();
            if (resp != null && resp.hits() != null) {
                for (Hit<Indicator> h : resp.hits().hits()) {
                    Indicator ind = h.source();
                    double score = h.score() == null ? 0.0 : h.score();
                    out.add(new Scored<>(ind, score));
                }
            }
            return out;
        } catch (Exception ex) {
            log.error("[Fetch Indicator Candidates Failed] error={}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    // 新增：从 ES 拉取候选法规
    public List<Scored<Regulation>> fetchTopRegulations(Behavior behavior, int candidateSize) {
        try {

            SearchResponse<Regulation> resp = esClient.search(s -> s
                            .index(ElasticSearchConfig.REGULATION_INDEX)
                            .size(candidateSize)
                            .knn(k -> k
                                    .field(REGULATION_VECTOR_FIELD)
                                    .queryVector(
                                            behavior.getDescriptionVector()
                                    )
                                    .k(candidateSize)
                                    .numCandidates(CANDIDATE_FETCH_SIZE)
                            )
                    , Regulation.class);

            List<Scored<Regulation>> out = new ArrayList<>();
            if (resp != null && resp.hits() != null) {
                for (Hit<Regulation> h : resp.hits().hits()) {
                    Regulation reg = h.source();
                    double score = h.score() == null ? 0.0 : h.score();
                    out.add(new Scored<>(reg, score));
                }
            }
            return out;
        } catch (Exception ex) {
            log.error("[Fetch Regulation Candidates Failed] error={}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    // 新增用于构建 calculation_details 的简单 JSON
    private ObjectNode buildCalculationDetails(double score, List<String> influencingRegs) {
        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("score", score);
        if (influencingRegs != null) {
            node.putPOJO("influencingRegulations", influencingRegs);
        } else {
            node.putPOJO("influencingRegulations", Collections.emptyList());
        }
        // 可在此添加更多中间过程信息
        return node;
    }
}
