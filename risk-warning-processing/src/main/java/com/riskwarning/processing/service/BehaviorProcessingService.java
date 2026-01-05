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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceUnit;

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

    private final TransactionTemplate transactionTemplate;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;


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
                                     TransactionTemplate transactionTemplate) {
        this.esClient = esClient;
        this.indicatorResultRepository = indicatorResultRepository;
        this.assessmentResultRepository = assessmentResultRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;

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






    /**
     * 更新 assessment 状态（使用 TransactionTemplate）
     */
    public void updateAssessmentStatus(Long assessmentId, AssessmentStatus status) {
        transactionTemplate.execute(txStatus -> {
            try {
                Optional<AssessmentResult> opt = assessmentResultRepository.findById(assessmentId);
                if (opt.isPresent()) {
                    AssessmentResult ar = opt.get();
                    ar.setStatus(status);
                    assessmentResultRepository.save(ar);
                    log.info("[Assessment Status Updated] assessmentId={}, status={}", assessmentId, status);
                } else {
                    log.warn("[Assessment Not Found] assessmentId={}", assessmentId);
                }
                return true;
            } catch (Exception e) {
                log.error("[Update Assessment Status Failed] assessmentId={}, error={}", assessmentId, e.getMessage(), e);
                txStatus.setRollbackOnly();
                throw new RuntimeException("Failed to update assessment status", e);
            }
        });
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
    public MappingResult processProjectBehaviors(Long projectId, Long assessmentId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId must not be null");
        }

        if (assessmentId == null) {
            throw new IllegalArgumentException("assessmentId must not be null");
        }

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
                    MappingResult result = computeMappingWithoutPersist(behavior, indicators, regulations);

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

                    // ✅ 保存计算结果（只存储 DTO，不存储 JPA Entity）
                    calculationResults.add(new BehaviorCalculationResult(behaviorId, indicatorMetadata, result));

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
        // ✅ 使用 EntityManager 手动管理事务
        if (!calculationResults.isEmpty()) {
            log.info("[Saving Results] count={} behaviors, assessmentId={}", calculationResults.size(), assessmentId);

            // ✅ 诊断：检查 TransactionTemplate 的事务管理器
            log.info("[TransactionTemplate Check] transactionManager={}",
                    transactionTemplate.getTransactionManager() != null
                        ? transactionTemplate.getTransactionManager().getClass().getName()
                        : "NULL");

            final Long finalProjectId = projectId;
            final Long finalAssessmentId = assessmentId;

            // ✅ 使用 EntityManagerFactory 创建新的 EntityManager，支持手动事务
            EntityManager em = entityManagerFactory.createEntityManager();
            EntityTransaction tx = null;
            try {
                tx = em.getTransaction();
                tx.begin();
                log.info("✓ [Manual TX Started] thread={}", Thread.currentThread().getName());

                for (int i = 0; i < calculationResults.size(); i++) {
                    BehaviorCalculationResult calcResult = calculationResults.get(i);

                    String behaviorId = calcResult.behaviorId;
                    Map<String, IndicatorMetadataDTO> indicatorMetadata = calcResult.indicatorMetadata;
                    MappingResult mr = calcResult.result;

                    log.info("[Saving Behavior {}/{}] behaviorId={}", i + 1, calculationResults.size(), behaviorId);

                    if (mr == null || mr.getIndicatorScores() == null || mr.getIndicatorScores().isEmpty()) {
                        log.debug("[No Scores to Save] behaviorId={}", behaviorId);
                        continue;
                    }

                    Map<String, Double> scores = mr.getIndicatorScores();
                    Map<String, List<String>> indicatorToRegs = mr.getIndicatorInfluencingRegulations();

                    int savedCount = 0;
                    for (Map.Entry<String, Double> e : scores.entrySet()) {
                        String indicatorEsId = e.getKey();
                        double normalizedScore = e.getValue() == null ? 0.0 : e.getValue();

                        IndicatorMetadataDTO metadata = indicatorMetadata.get(indicatorEsId);
                        double maxPossible = 100.0;
                        if (metadata != null && metadata.maxScore != null && metadata.maxScore > 0) {
                            maxPossible = metadata.maxScore;
                        }
                        double absoluteScore = normalizedScore * maxPossible;

                        // 使用 JPQL 查询
                        List<IndicatorResult> existingList = em.createQuery(
                                "SELECT ir FROM IndicatorResult ir WHERE ir.assessmentId = :assessmentId AND ir.indicatorEsId = :indicatorEsId",
                                IndicatorResult.class)
                                .setParameter("assessmentId", finalAssessmentId)
                                .setParameter("indicatorEsId", indicatorEsId)
                                .getResultList();

                        if (!existingList.isEmpty()) {
                            IndicatorResult existing = existingList.get(0);
                            String[] existingBehaviorIds = existing.getMatchedBehaviorsIds();

                            boolean alreadyContains = existingBehaviorIds != null &&
                                    Arrays.asList(existingBehaviorIds).contains(behaviorId);

                            if (!alreadyContains) {
                                double existingScore = existing.getCalculatedScore().doubleValue();
                                int existingCount = existingBehaviorIds != null ? existingBehaviorIds.length : 0;
                                double averagedScore = (existingScore * existingCount + absoluteScore) / (existingCount + 1);

                                Set<String> mergedBehaviorIds = new HashSet<>();
                                if (existingBehaviorIds != null) {
                                    mergedBehaviorIds.addAll(Arrays.asList(existingBehaviorIds));
                                }
                                mergedBehaviorIds.add(behaviorId);

                                existing.setCalculatedScore(BigDecimal.valueOf(averagedScore));
                                existing.setMatchedBehaviorsIds(mergedBehaviorIds.toArray(new String[0]));
                                existing.setCalculatedAt(OffsetDateTime.now());

                                ObjectNode details = buildCalculationDetails(averagedScore / maxPossible,
                                        indicatorToRegs != null ? indicatorToRegs.get(indicatorEsId) : null);
                                details.put("absoluteScore", averagedScore);
                                details.put("averagedFrom", "merged");
                                existing.setCalculationDetails(details.toString());

                                em.merge(existing);
                                savedCount++;
                            }
                        } else {
                            ObjectNode details = buildCalculationDetails(normalizedScore,
                                    indicatorToRegs != null ? indicatorToRegs.get(indicatorEsId) : null);
                            details.put("absoluteScore", absoluteScore);

                            IndicatorResult ir = IndicatorResult.builder()
                                    .projectId(finalProjectId)
                                    .assessmentId(finalAssessmentId)
                                    .indicatorEsId(indicatorEsId)
                                    .indicatorName(metadata != null ? metadata.name : indicatorEsId)
                                    .indicatorLevel(metadata != null && metadata.indicatorLevel != null ? metadata.indicatorLevel : 0)
                                    .dimension(metadata != null ? metadata.dimension : null)
                                    .type(metadata != null ? metadata.type : null)
                                    .calculatedScore(BigDecimal.valueOf(absoluteScore))
                                    .maxPossibleScore(BigDecimal.valueOf(maxPossible))
                                    .usedCalculationRuleType("auto")
                                    .calculationDetails(details.toString())
                                    .matchedBehaviorsIds(new String[]{behaviorId})
                                    .riskTriggered(false)
                                    .riskStatus(IndicatorRiskStatus.fromCode("NOT_EVALUATED"))
                                    .calculatedAt(OffsetDateTime.now())
                                    .createdAt(OffsetDateTime.now())
                                    .build();

                            em.persist(ir);
                            savedCount++;
                        }
                    }

                    log.info("[Saved Behavior {}/{}] behaviorId={}, savedIndicators={} - Success",
                            i + 1, calculationResults.size(), behaviorId, savedCount);
                }

                // 提交事务
                em.flush();
                tx.commit();
                log.info("✓ [Manual TX Committed]");

            } catch (Exception e) {
                log.error("[TX Failed] error={}", e.getMessage(), e);

                Throwable rootCause = e;
                while (rootCause.getCause() != null) {
                    rootCause = rootCause.getCause();
                }
                log.error("[Root Cause] {}: {}", rootCause.getClass().getName(), rootCause.getMessage());

                // 回滚事务
                if (tx != null && tx.isActive()) {
                    tx.rollback();
                    log.info("✓ [Manual TX Rolled Back]");
                }

                throw new RuntimeException("Failed to save batch results", e);
            } finally {
                // 关闭 EntityManager
                if (em != null && em.isOpen()) {
                    em.close();
                }
            }
        }


        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        log.info("[Process COMPLETE] projectId={}, assessmentId={}, duration={}ms, success={}, failed={}",
                projectId, assessmentId, duration, successCount.get(), failureCount.get());

        // 4. 更新 assessment 状态为完成（直接调用，使用 TransactionTemplate）
        updateAssessmentStatus(assessmentId, AssessmentStatus.COMPLETED);

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
     * 改名：计算并入库（projectId 与 assessmentId 由调用方传入）
     * ✅ 使用 TransactionTemplate，只传递 DTO/基本类型，在事务内创建实体
     */
    public MappingResult computeAndPersistWithIds(Behavior behavior,
                                                  List<Scored<Indicator>> indicators,
                                                  List<Scored<Regulation>> regulations,
                                                  Long projectId,
                                                  Long assessmentId) {
        MappingResult mr = computeMappingFromCandidates(behavior, indicators, regulations);

        // ✅ 提取基本类型数据，不传递实体进入事务
        final String currentBehaviorId = behavior != null && behavior.getId() != null ? behavior.getId() : null;
        final Map<String, Double> scores = new HashMap<>(mr.getIndicatorScores());
        final Map<String, List<String>> indicatorToRegs = mr.getIndicatorInfluencingRegulations() != null
            ? new HashMap<>(mr.getIndicatorInfluencingRegulations())
            : new HashMap<>();

        // ✅ 提取指标元数据为简单 Map
        final Map<String, IndicatorMetadataDTO> indicatorMetadataMap = new HashMap<>();
        for (Scored<Indicator> s : indicators) {
            if (s.getItem() != null && s.getItem().getId() != null) {
                Indicator ind = s.getItem();
                indicatorMetadataMap.put(ind.getId(), new IndicatorMetadataDTO(
                    ind.getId(),
                    ind.getName(),
                    ind.getIndicatorLevel(),
                    ind.getDimension(),
                    ind.getType(),
                    ind.getMaxScore()
                ));
            }
        }

        // ✅ 使用 TransactionTemplate 执行数据库操作
        transactionTemplate.execute(status -> {
            try {
                List<IndicatorResult> savedResults = new ArrayList<>();

                for (Map.Entry<String, Double> e : scores.entrySet()) {
                    String indicatorEsId = e.getKey();
                    double normalizedScore = e.getValue() == null ? 0.0 : e.getValue();

                    // 从元数据 Map 获取
                    IndicatorMetadataDTO metadata = indicatorMetadataMap.get(indicatorEsId);

                    // 确保 maxPossible 始终为正数（数据库约束要求）
                    double maxPossible = 100.0;  // 默认值
                    if (metadata != null && metadata.maxScore != null && metadata.maxScore > 0) {
                        maxPossible = metadata.maxScore;
                    } else {
                        log.warn("[MaxScore Warning] indicatorId={}, indicatorName={}, maxScore={}, using default={}",
                                indicatorEsId,
                                metadata != null ? metadata.name : "UNKNOWN",
                                metadata != null ? metadata.maxScore : "null",
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

                        if (!alreadyContains) {
                            // 合并：平均分数，添加 behavior ID
                            double existingScore = existing.getCalculatedScore().doubleValue();
                            int existingCount = existingBehaviorIds != null ? existingBehaviorIds.length : 0;
                            double averagedScore = (existingScore * existingCount + absoluteScore) / (existingCount + 1);

                            // 合并 behavior IDs（去重）
                            Set<String> mergedBehaviorIds = new HashSet<>(Arrays.asList(existingBehaviorIds));
                            mergedBehaviorIds.add(currentBehaviorId);

                            existing.setCalculatedScore(BigDecimal.valueOf(averagedScore));
                            existing.setMatchedBehaviorsIds(mergedBehaviorIds.toArray(new String[0]));
                            existing.setCalculatedAt(OffsetDateTime.now());

                            // 更新 calculation_details
                            ObjectNode details = buildCalculationDetails(averagedScore / maxPossible, indicatorToRegs.get(indicatorEsId));
                            details.put("absoluteScore", averagedScore);
                            details.put("averagedFrom", "existing_and_new");
                            existing.setCalculationDetails(details.toString());

                            IndicatorResult saved = indicatorResultRepository.saveAndFlush(existing);
                            savedResults.add(saved);
                            continue;
                        }
                    }

                    // ✅ 在事务内创建新记录
                    ObjectNode details = buildCalculationDetails(normalizedScore, indicatorToRegs.get(indicatorEsId));
                    details.put("absoluteScore", absoluteScore);

                    IndicatorResult ir = IndicatorResult.builder()
                            .projectId(projectId)
                            .assessmentId(assessmentId)
                            .indicatorEsId(indicatorEsId)
                            .indicatorName(metadata != null ? metadata.name : (indicatorEsId == null ? "" : indicatorEsId))
                            .indicatorLevel(metadata != null && metadata.indicatorLevel != null ? metadata.indicatorLevel : 0)
                            .dimension(metadata != null ? metadata.dimension : null)
                            .type(metadata != null ? metadata.type : null)
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

                    IndicatorResult saved = indicatorResultRepository.saveAndFlush(ir);
                    savedResults.add(saved);
                }

                log.info("[Preparing to Save IndicatorResults] count={}, projectId={}, assessmentId={}",
                        savedResults.size(), projectId, assessmentId);

                if (savedResults.isEmpty()) {
                    log.warn("[No IndicatorResults to Save] projectId={}, assessmentId={}, reason=NO_INDICATOR_SCORES",
                            projectId, assessmentId);
                    return true;
                }

                // 验证保存结果
                int generatedIdCount = 0;
                for (IndicatorResult saved : savedResults) {
                    boolean hasId = saved.getId() != null;
                    if (hasId) generatedIdCount++;
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

                    status.setRollbackOnly();
                    throw new RuntimeException("Failed to generate IDs for saved entities - data not persisted");
                } else {
                    log.info("[ID Generation Summary] Generated IDs: {}/{}", generatedIdCount, savedResults.size());
                }

                return true;

            } catch (Exception ex) {
                // ✅ 打印完整的 root cause
                log.error("[Failed to Save IndicatorResults] projectId={}, assessmentId={}, error={}",
                        projectId, assessmentId, ex.getMessage(), ex);
                Throwable rootCause = ex;
                while (rootCause.getCause() != null) {
                    rootCause = rootCause.getCause();
                }
                log.error("[Root Cause] {}: {}", rootCause.getClass().getName(), rootCause.getMessage());
                status.setRollbackOnly();
                throw new RuntimeException("Failed to save IndicatorResults", ex);
            }
        });

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
