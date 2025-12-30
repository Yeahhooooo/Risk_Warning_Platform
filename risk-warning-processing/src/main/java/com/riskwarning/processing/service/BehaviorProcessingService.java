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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BehaviorProcessingService {

    private static final double REG_APPLICABILITY_THRESHOLD = 0.15;
    private static final double REG_TO_INDICATOR_THRESHOLD = 0.2;

    // 线程池配置
    private static final int THREAD_POOL_SIZE = 10;  // 线程池大小，可配置
    private static final int MAX_POOL_SIZE = 20;     // 最大线程数
    private static final int QUEUE_CAPACITY = 100;   // 队列容量
    private final ExecutorService executorService;

    // 新增：注入 ElasticsearchClient 与索引名配置
    private final ElasticsearchClient esClient;

    private final IndicatorResultRepository indicatorResultRepository;

    private final AssessmentResultRepository assessmentResultRepository;

    private final ObjectMapper objectMapper;

    // 使用 Lazy 注入自身的代理，用于在多线程环境下调用带事务的方法
    private final BehaviorProcessingService self;

    @Value("${es.index.indicator:t_indicator}")
    private String indicatorIndex;

    @Value("${es.index.regulation:t_regulation}")
    private String regulationIndex;

    @Value("${es.index.behavior:t_behavior}")
    private String behaviorIndex;

    private static final Logger log = LoggerFactory.getLogger(BehaviorProcessingService.class);

    @Autowired
    public BehaviorProcessingService(ElasticsearchClient esClient,
                                     IndicatorResultRepository indicatorResultRepository,
                                     AssessmentResultRepository assessmentResultRepository,
                                     ObjectMapper objectMapper,
                                     @org.springframework.context.annotation.Lazy BehaviorProcessingService self) {
        this.esClient = esClient;
        this.indicatorResultRepository = indicatorResultRepository;
        this.assessmentResultRepository = assessmentResultRepository;
        this.objectMapper = objectMapper;
        this.self = self;

        // 初始化线程池
        this.executorService = new ThreadPoolExecutor(
                THREAD_POOL_SIZE,
                MAX_POOL_SIZE,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时由调用线程执行
        );

        log.info("[Thread Pool Initialized] corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                THREAD_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY);
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

    /**
     * 新方法：只传入 projectId，从 ES 获取所有该项目的 behaviors，
     * 创建一个 assessment，然后依次处理每个 behavior
     *
     * 临时修改：由于ES中behaviors的projectId为null，改为随机获取5个行为并设置projectId为7
     */
    @Transactional
    public MappingResult processProjectBehaviors(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId must not be null");
        }

        log.info("[Process Project START] projectId={}", projectId);

        // 1. 从 ES 随机获取 behaviors
        List<Behavior> behaviors = fetchRandomBehaviors(5, projectId);

        if (behaviors.isEmpty()) {
            throw new IllegalArgumentException("No behaviors found for projectId: " + projectId);
        }


        // 2. 创建一个 assessment
        AssessmentResult ar = AssessmentResult.builder()
                .projectId(projectId)
                .assessmentDate(OffsetDateTime.now())
                .status(AssessmentStatus.IN_PROGRESS)
                .createdAt(OffsetDateTime.now())
                .build();

        AssessmentResult saved = assessmentResultRepository.save(ar);
        Long assessmentId = saved.getId();


        // 3. 使用线程池并发处理每个 behavior
        MappingResult aggregatedResult = new MappingResult();
        Map<String, Double> aggregatedScores = new ConcurrentHashMap<>();
        Map<String, List<String>> aggregatedRegulations = new ConcurrentHashMap<>();
        List<String> allWarnings = Collections.synchronizedList(new ArrayList<>());

        // 使用CountDownLatch等待所有任务完成
        CountDownLatch latch = new CountDownLatch(behaviors.size());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);


        long startTime = System.currentTimeMillis();

        // 收集每个behavior的计算结果（候选+结果），稍后在主线程中统一调用原有存储方法
        List<BehaviorCalculationResult> calculationResults = Collections.synchronizedList(new ArrayList<>());

        for (Behavior behavior : behaviors) {
            executorService.submit(() -> {
                try {
                    // 并发进行计算：获取候选指标和法规
                    List<Scored<Indicator>> indicators = fetchTopIndicators(behavior, 6);
                    List<Scored<Regulation>> regulations = fetchTopRegulations(behavior, 10);

                    // 只计算，不保存
                    MappingResult result = computeMappingWithoutPersist(behavior, indicators, regulations);

                    // 保存计算结果（包括候选数据），供后续存储使用
                    calculationResults.add(new BehaviorCalculationResult(behavior, indicators, regulations, result));

                    // 聚合结果（使用线程安全的集合）
                    if (result.getIndicatorScores() != null) {
                        for (Map.Entry<String, Double> entry : result.getIndicatorScores().entrySet()) {
                            String indicatorId = entry.getKey();
                            Double score = entry.getValue();
                            aggregatedScores.merge(indicatorId, score, (oldVal, newVal) ->
                                (oldVal + newVal) / 2.0
                            );
                        }
                    }

                    if (result.getIndicatorInfluencingRegulations() != null) {
                        for (Map.Entry<String, List<String>> entry : result.getIndicatorInfluencingRegulations().entrySet()) {
                            aggregatedRegulations.computeIfAbsent(entry.getKey(), k ->
                                Collections.synchronizedList(new ArrayList<>())
                            ).addAll(entry.getValue());
                        }
                    }

                    if (result.getWarnings() != null) {
                        allWarnings.addAll(result.getWarnings());
                    }

                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.error("[Behavior Processing Failed] behaviorId={}, error={}", behavior.getId(), e.getMessage());
                    allWarnings.add("Failed to process behavior " + behavior.getId() + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有任务完成
        try {
            boolean finished = latch.await(30, TimeUnit.MINUTES);
            if (!finished) {
                log.error("[Processing Timeout] remainingTasks={}", latch.getCount());
                allWarnings.add("Processing timeout: " + latch.getCount() + " behaviors not completed");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Processing Interrupted] {}", e.getMessage());
            allWarnings.add("Processing interrupted: " + e.getMessage());
        }

        // 在主线程中统一保存所有计算结果
        if (!calculationResults.isEmpty()) {
            log.info("[Saving Results] count={} behaviors, assessmentId={}", calculationResults.size(), assessmentId);

            for (int i = 0; i < calculationResults.size(); i++) {
                BehaviorCalculationResult calcResult = calculationResults.get(i);
                log.info("[Saving Behavior {}/{}] behaviorId={}", i + 1, calculationResults.size(),
                        calcResult.behavior.getId());

                // 直接持久化已计算的结果，不再重新计算
                persistMappingResult(
                        calcResult.behavior,
                        calcResult.indicators,
                        calcResult.result,
                        projectId,
                        assessmentId
                );

                log.info("[Saved Behavior {}/{}] behaviorId={} - Success", i + 1, calculationResults.size(),
                        calcResult.behavior.getId());
            }
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        log.info("[Process COMPLETE] projectId={}, assessmentId={}, duration={}ms, success={}, failed={}",
                projectId, assessmentId, duration, successCount.get(), failureCount.get());

        // 4. 更新 assessment 状态为完成
        saved.setStatus(AssessmentStatus.COMPLETED);
        assessmentResultRepository.save(saved);


        // 返回聚合结果
        aggregatedResult.setIndicatorScores(aggregatedScores);
        aggregatedResult.setIndicatorInfluencingRegulations(aggregatedRegulations);
        aggregatedResult.setWarnings(allWarnings);

        return aggregatedResult;
    }

    /**
     * 从 ES 中随机获取指定数量的 behaviors 并设置 projectId
     * 临时方案：由于ES中所有behavior的projectId为null，使用随机查询
     */
    private List<Behavior> fetchRandomBehaviors(int count, Long projectId) {
        try {
            int fetchSize = Math.min(count * 5, 100);

            SearchResponse<Behavior> resp = esClient.search(s -> s
                    .index(behaviorIndex)
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
                    behavior.setProject_id(projectId);
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
            log.info("[Fetching Behaviors from ES] projectId={}, index={}", projectId, behaviorIndex);

            SearchResponse<Behavior> resp = esClient.search(s -> s
                    .index(behaviorIndex)
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

    // 内部类：存储单个 behavior 的计算结果
    private static class BehaviorCalculationResult {
        final Behavior behavior;
        final List<Scored<Indicator>> indicators;
        final List<Scored<Regulation>> regulations;
        final MappingResult result;

        BehaviorCalculationResult(Behavior behavior,
                                  List<Scored<Indicator>> indicators,
                                  List<Scored<Regulation>> regulations,
                                  MappingResult result) {
            this.behavior = behavior;
            this.indicators = indicators;
            this.regulations = regulations;
            this.result = result;
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
                float[] indVec = ind.getNameVector();

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
                indicatorToRegs.computeIfAbsent(ind.getId(), k -> new ArrayList<>()).add(reg.getId());
            }
        }

        // 计算每个指标的最终得分
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

                if (fallback > 0) {
                    indicatorToRegs.computeIfAbsent(ind.getId(), k -> new ArrayList<>()).add("FALLBACK");
                }
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
                finalScores.put(ind.getId(), clampedScore);
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

    /**
     * 只计算mapping，不保存到数据库
     */
    private MappingResult computeMappingWithoutPersist(Behavior behavior,
                                                        List<Scored<Indicator>> indicators,
                                                        List<Scored<Regulation>> regulations) {
        return computeMappingFromCandidates(behavior, indicators, regulations);
    }

    /**
     * 持久化已计算的 MappingResult（不重新计算）
     * 这个方法在主线程的事务中调用，不需要 REQUIRES_NEW
     */
    private void persistMappingResult(Behavior behavior,
                                       List<Scored<Indicator>> indicators,
                                       MappingResult mr,
                                       Long projectId,
                                       Long assessmentId) {
        if (mr == null || mr.getIndicatorScores() == null || mr.getIndicatorScores().isEmpty()) {
            log.warn("[No Scores to Persist] behaviorId={}, assessmentId={}",
                    behavior != null ? behavior.getId() : "null", assessmentId);
            return;
        }

        Map<String, Double> scores = mr.getIndicatorScores();
        Map<String, List<String>> indicatorToRegs = mr.getIndicatorInfluencingRegulations();
        List<IndicatorResult> toSave = new ArrayList<>();
        String currentBehaviorId = behavior != null && behavior.getId() != null ? behavior.getId() : null;

        for (Map.Entry<String, Double> e : scores.entrySet()) {
            String indicatorEsId = e.getKey();
            double normalizedScore = e.getValue() == null ? 0.0 : e.getValue();

            // 找到元数据
            Indicator matched = null;
            for (Scored<Indicator> s : indicators) {
                if (s.getItem() != null && indicatorEsId.equals(s.getItem().getId())) {
                    matched = s.getItem();
                    break;
                }
            }

            // 确保 maxPossible 始终为正数
            double maxPossible = 100.0;
            if (matched != null && matched.getMaxScore() != null && matched.getMaxScore() > 0) {
                maxPossible = matched.getMaxScore();
            }

            double absoluteScore = normalizedScore * maxPossible;

            // 查找是否存在相同 assessmentId 和 indicatorEsId 的记录
            Optional<IndicatorResult> existingOpt = indicatorResultRepository
                    .findByAssessmentIdAndIndicatorEsId(assessmentId, indicatorEsId);

            if (existingOpt.isPresent() && currentBehaviorId != null) {
                IndicatorResult existing = existingOpt.get();
                String[] existingBehaviorIds = existing.getMatchedBehaviorsIds();

                // 检查当前 behaviorId 是否已经在列表中
                boolean alreadyContains = existingBehaviorIds != null &&
                        Arrays.asList(existingBehaviorIds).contains(currentBehaviorId);

                if (!alreadyContains) {
                    // 合并：平均分数，添加 behavior ID
                    double existingScore = existing.getCalculatedScore().doubleValue();
                    int existingCount = existingBehaviorIds != null ? existingBehaviorIds.length : 0;
                    double averagedScore = (existingScore * existingCount + absoluteScore) / (existingCount + 1);

                    // 合并 behavior IDs
                    Set<String> mergedBehaviorIds = new HashSet<>();
                    if (existingBehaviorIds != null) {
                        mergedBehaviorIds.addAll(Arrays.asList(existingBehaviorIds));
                    }
                    mergedBehaviorIds.add(currentBehaviorId);

                    existing.setCalculatedScore(BigDecimal.valueOf(averagedScore));
                    existing.setMatchedBehaviorsIds(mergedBehaviorIds.toArray(new String[0]));
                    existing.setCalculatedAt(OffsetDateTime.now());

                    // 更新 calculation_details
                    ObjectNode details = buildCalculationDetails(averagedScore / maxPossible,
                            indicatorToRegs != null ? indicatorToRegs.get(indicatorEsId) : null);
                    details.put("absoluteScore", averagedScore);
                    details.put("averagedFrom", "merged");
                    existing.setCalculationDetails(details.toString());

                    toSave.add(existing);
                    continue;
                }
            }

            // 创建新记录
            ObjectNode details = buildCalculationDetails(normalizedScore,
                    indicatorToRegs != null ? indicatorToRegs.get(indicatorEsId) : null);
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

        if (toSave.isEmpty()) {
            log.warn("[No New Records to Save] behaviorId={}, assessmentId={}", currentBehaviorId, assessmentId);
            return;
        }

        log.info("[Persisting {} IndicatorResults] behaviorId={}, assessmentId={}",
                toSave.size(), currentBehaviorId, assessmentId);

        // 保存到数据库
        List<IndicatorResult> savedResults = indicatorResultRepository.saveAll(toSave);

        // 立即刷新（在外层事务中，flush 应该正常工作）
        indicatorResultRepository.flush();

        log.info("[IndicatorResults Saved] count={}, behaviorId={}, assessmentId={}",
                savedResults.size(), currentBehaviorId, assessmentId);

        // 验证保存结果
        int generatedIdCount = 0;
        for (IndicatorResult saved : savedResults) {
            if (saved.getId() != null) {
                generatedIdCount++;
            }
        }

        if (generatedIdCount == 0) {
            log.error("[CRITICAL] No IDs generated for behaviorId={}, assessmentId={}", currentBehaviorId, assessmentId);
            throw new RuntimeException("Failed to generate IDs for saved entities");
        }

        log.info("[ID Generation Verified] {}/{} records have IDs", generatedIdCount, savedResults.size());
    }


    // 改名：计算并入库（projectId 与 assessmentId 由调用方传入）
    // 使用 REQUIRES_NEW 确保在多线程环境下每个调用都有独立的事务
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
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

            log.info("[Preparing to Save IndicatorResults] count={}, projectId={}, assessmentId={}",
                    toSave.size(), projectId, assessmentId);

            if (toSave.isEmpty()) {
                log.warn("[No IndicatorResults to Save] projectId={}, assessmentId={}, reason=NO_INDICATOR_SCORES",
                        projectId, assessmentId);
            } else {
                // 打印每个要保存的 IndicatorResult 的关键信息
                for (IndicatorResult ir : toSave) {
                    log.info("[IndicatorResult to Save] id={}, projectId={}, assessmentId={}, indicatorEsId={}, indicatorName={}, score={}, maxScore={}",
                            ir.getId(), ir.getProjectId(), ir.getAssessmentId(), ir.getIndicatorEsId(),
                            ir.getIndicatorName(), ir.getCalculatedScore(), ir.getMaxPossibleScore());
                }

                // 保存并立即刷新到数据库
                List<IndicatorResult> savedResults;
                try {
                    log.info("[Calling saveAll] Attempting to save {} records", toSave.size());
                    savedResults = indicatorResultRepository.saveAll(toSave);
                    log.info("[saveAll Completed] Returned {} records", savedResults.size());
                } catch (Exception saveEx) {
                    log.error("[saveAll FAILED] Error during save: {}", saveEx.getMessage(), saveEx);
                    throw saveEx; // 重新抛出，让外层 catch 处理
                }

                // 尝试手动刷新（如果 repository 支持）
                try {
                    log.info("[Calling flush] Attempting to flush to database");
                    indicatorResultRepository.flush();
                    log.info("[Database Flushed] Changes written to database");
                } catch (Exception flushEx) {
                    log.error("[Flush FAILED] Error during flush: {}", flushEx.getMessage(), flushEx);
                    throw flushEx; // 刷新失败也要抛出
                }

                log.info("[IndicatorResults Saved Successfully] savedCount={}, projectId={}, assessmentId={}",
                        savedResults.size(), projectId, assessmentId);

                // 验证保存结果
                int generatedIdCount = 0;
                for (IndicatorResult saved : savedResults) {
                    boolean hasId = saved.getId() != null;
                    if (hasId) generatedIdCount++;

                    log.info("[IndicatorResult Saved Detail] id={}, indicatorEsId={}, score={}, generatedId={}",
                            saved.getId(), saved.getIndicatorEsId(), saved.getCalculatedScore(),
                            hasId ? "YES" : "NO");
                }

                if (generatedIdCount == 0) {
                    log.error("[CRITICAL] No IDs were generated! This means data was NOT persisted to database!");
                    log.error("[CRITICAL] Possible causes: 1) Transaction not active 2) Database constraints violated 3) Entity state issue");

                    // 尝试从数据库查询验证
                    try {
                        long count = indicatorResultRepository.countByAssessmentId(assessmentId);
                        log.error("[Database Verification] Records in DB for assessmentId={}: count={}", assessmentId, count);
                    } catch (Exception dbEx) {
                        log.error("[Database Verification Failed] {}", dbEx.getMessage());
                    }

                    // 立即抛出异常，确保事务回滚
                    throw new RuntimeException("Failed to generate IDs for saved entities - data not persisted");
                } else {
                    log.info("[ID Generation Summary] Generated IDs: {}/{}", generatedIdCount, savedResults.size());
                }
            }
        } catch (Exception ex) {
            // 记录详细的异常信息
            log.error("[Failed to Save IndicatorResults] projectId={}, assessmentId={}, error={}, errorType={}",
                    projectId, assessmentId, ex.getMessage(), ex.getClass().getName(), ex);
            // 重新抛出异常，让调用方知道保存失败
            throw new RuntimeException("Failed to save IndicatorResults: " + ex.getMessage(), ex);
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
            String text = (behavior.getDescription() == null ? "" : behavior.getDescription())
                    + " " + (behavior.getTags() == null ? "" : String.join(" ", behavior.getTags()));

            SearchResponse<Regulation> resp = esClient.search(s -> s
                            .index(regulationIndex)
                            .size(candidateSize)
                            .query(q -> q.multiMatch(mm -> mm
                                    .fields(Arrays.asList("title", "full_text", "tags"))
                                    .query(text)
                            ))
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

    /**
     * 优雅关闭线程池
     * Spring容器销毁时自动调用
     */
    @javax.annotation.PreDestroy
    public void destroy() {
        log.info("[Thread Pool Shutdown] Initiating graceful shutdown...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                log.warn("[Thread Pool Shutdown] Pool did not terminate gracefully, forcing shutdown...");
                executorService.shutdownNow();
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.error("[Thread Pool Shutdown] Pool did not terminate");
                }
            }
            log.info("[Thread Pool Shutdown] Completed successfully");
        } catch (InterruptedException e) {
            log.error("[Thread Pool Shutdown] Interrupted during shutdown", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
