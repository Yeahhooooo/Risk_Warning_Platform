package com.riskwarning.processing.service;

import com.riskwarning.common.config.ElasticSearchConfig;
import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.common.enums.indicator.IndicatorRiskStatus;
import com.riskwarning.common.enums.project.ProjectStatus;
import com.riskwarning.common.exception.BusinessException;
import com.riskwarning.common.message.AssessmentCompletedEventMessage;
import com.riskwarning.common.po.behavior.Behavior;
import com.riskwarning.common.po.indicator.Indicator;
import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.po.indicator.IndicatorResultDetail;
import com.riskwarning.common.po.project.Project;
import com.riskwarning.common.po.regulation.Regulation;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.po.risk.RelatedBehavior;
import com.riskwarning.common.po.risk.RelatedIndicator;
import com.riskwarning.common.po.risk.RelatedRegulation;
import com.riskwarning.common.utils.KafkaUtils;
import com.riskwarning.common.utils.StringUtils;
import com.riskwarning.processing.entity.dto.DocumentProcessingResult;
import com.riskwarning.processing.repository.AssessmentRepository;
import com.riskwarning.processing.repository.IndicatorResultRepository;
import com.riskwarning.processing.repository.ProjectRepository;
import com.riskwarning.processing.util.behavior.FallbackCalculator;
import com.riskwarning.processing.util.behavior.QualitativeCalculator;
import com.riskwarning.processing.util.behavior.QuantitativeCalculator;
import com.riskwarning.processing.util.behavior.RegWeightCalculator;
import com.riskwarning.processing.util.behavior.SimilarityCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;


import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class BehaviorProcessingService {

    private static final double REG_APPLICABILITY_THRESHOLD = 0.15;

    private static final double REG_TO_INDICATOR_THRESHOLD = 0.2;

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
    private ProjectRepository projectRepository;

    @Autowired
    @Qualifier(value = "BehaviorProcessTaskThreadPool")
    private ThreadPoolTaskExecutor behaviorThreadPoolExecutor;

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
     * 修改：使用批量处理方式，收集所有计算结果后批量更新，避免乐观锁冲突
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

        // 1. 从 ES 获取该项目的所有 behaviors
        List<Behavior> behaviors = fetchRandomBehaviors( projectId);
        int totalBehaviorCount = behaviors.size();

        if (behaviors.isEmpty()) {
            throw new IllegalArgumentException("No behaviors found for projectId: " + projectId);
        }

        log.info("[使用传入的 Assessment] assessmentId={}, projectId={}, behaviorCount={}",
                assessmentId, projectId, behaviors.size());

        // 工作线程只计算；结果汇总和数据库写入均由当前线程完成。
        Map<String, List<RelatedIndicator>> indicatorResultsMap = new HashMap<>();
        Map<String, IndicatorMetadataDTO> indicatorMetadataMap = new HashMap<>();
        List<Future<BehaviorCalculationResult>> futures = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(30);
        try {
            for (Behavior behavior : behaviors) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Behavior processing interrupted");
                }
                futures.add(behaviorThreadPoolExecutor.submit(() -> {
                    List<Scored<Indicator>> indicators = fetchTopIndicators(behavior, 6);
                    List<Scored<Regulation>> regulations = fetchTopRegulations(behavior, 10);
                    return new BehaviorCalculationResult(indicators,
                            computeMappingFromCandidates(behavior, indicators, regulations));
                }));
            }

            for (Future<BehaviorCalculationResult> future : futures) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Behavior processing interrupted");
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new TimeoutException("Behavior processing exceeded 30 minutes");
                }
                BehaviorCalculationResult calculation = future.get(remaining, TimeUnit.NANOSECONDS);
                DocumentProcessingResult.MappingResult result = calculation.mapping;
                if (result == null || result.getRelatedIndicators() == null) {
                    continue;
                }
                for (Scored<Indicator> scored : calculation.indicators) {
                    Indicator indicator = scored.getItem();
                    if (indicator != null && indicator.getId() != null) {
                        indicatorMetadataMap.putIfAbsent(indicator.getId(), new IndicatorMetadataDTO(
                                indicator.getId(), indicator.getName(), indicator.getIndicatorLevel(),
                                indicator.getDimension(), indicator.getType(), indicator.getMaxScore()));
                    }
                }
                for (Map.Entry<String, RelatedIndicator> entry : result.getRelatedIndicators().entrySet()) {
                    indicatorResultsMap.computeIfAbsent(entry.getKey(), key -> new ArrayList<>())
                            .add(entry.getValue());
                }
            }

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Behavior processing interrupted");
            }
            // 所有行为成功后才保存，避免失败任务留下部分计算结果。
            batchSaveIndicatorResults(indicatorResultsMap, indicatorMetadataMap, projectId, assessmentId);
            completeAssessmentIfNeeded(userId, projectId, assessmentId);
            log.info("[Assessment Completed] assessmentId={}, behaviorCount={}", assessmentId, totalBehaviorCount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Behavior processing interrupted: assessmentId=" + assessmentId, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Behavior calculation failed: assessmentId=" + assessmentId, e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("Behavior processing timed out: assessmentId=" + assessmentId, e);
        } finally {
            for (Future<BehaviorCalculationResult> future : futures) {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }
        }
    }

    private static class BehaviorCalculationResult {
        final List<Scored<Indicator>> indicators;
        final DocumentProcessingResult.MappingResult mapping;

        BehaviorCalculationResult(List<Scored<Indicator>> indicators,
                                  DocumentProcessingResult.MappingResult mapping) {
            this.indicators = indicators;
            this.mapping = mapping;
        }
    }
    /**
     * 批量保存指标结果
     */
    private void batchSaveIndicatorResults(Map<String, List<RelatedIndicator>> indicatorResultsMap,
                                           Map<String, IndicatorMetadataDTO> indicatorMetadataMap,
                                           Long projectId, Long assessmentId) {
        if (indicatorResultsMap.isEmpty()) {
            log.info("[Batch Save] 无指标结果需要保存");
            return;
        }
        
        try {
            log.info("[Batch Save] 开始批量保存，共 {} 个指标", indicatorResultsMap.size());
            List<IndicatorResult> resultsToSave = new ArrayList<>();
            
            int processedIndicators = 0;
            for (Map.Entry<String, List<RelatedIndicator>> entry : indicatorResultsMap.entrySet()) {
                String indicatorId = entry.getKey();
                List<RelatedIndicator> relatedIndicators = entry.getValue();
                
                if (relatedIndicators.isEmpty()) {
                    continue;
                }
                
                processedIndicators++;
                if (processedIndicators % 10 == 0) {
                    log.info("[Batch Save] 已处理 {} 个指标", processedIndicators);
                }
                
                // 计算平均得分
                double totalScore = 0;
                for (RelatedIndicator ri : relatedIndicators) {
                    totalScore += ri.getScore();
                }
                double avgScore = totalScore / relatedIndicators.size();
                
                // 获取指标元数据
                IndicatorMetadataDTO metadata = indicatorMetadataMap.get(indicatorId);
                double maxPossible = 100.0;
                if (metadata != null && metadata.maxScore != null && metadata.maxScore > 0) {
                    maxPossible = metadata.maxScore;
                }
                double absoluteScore = avgScore * maxPossible;
                
                // 检查是否已存在
                Optional<IndicatorResult> existingResult = indicatorResultRepository
                        .findByAssessmentIdAndIndicatorEsId(assessmentId, indicatorId);
                
                if (existingResult.isPresent()) {
                    // 更新现有记录
                    IndicatorResult existing = existingResult.get();
                    IndicatorResultDetail detail = existing.getCalculationDetails();
                    if (detail == null) {
                        detail = IndicatorResultDetail.builder()
                                .relatedIndicators(new ArrayList<>())
                                .build();
                    }
                    
                    // 合并相关指标
                    int existingCount = detail.getRelatedIndicators().size();
                    double existingScore = existing.getCalculatedScore();
                    int newCount = relatedIndicators.size();
                    // absoluteScore 是新批次均分，合并前须乘以条数还原为总分。
                    double newAvgScore = (existingScore * existingCount + absoluteScore * newCount)
                            / (existingCount + newCount);
                    
                    existing.setCalculatedScore(newAvgScore);
                    existing.setCalculatedAt(LocalDateTime.now());
                    detail.getRelatedIndicators().addAll(relatedIndicators);
                    existing.setCalculationDetails(detail);
                    
                    resultsToSave.add(existing);
                } else {
                    // 创建新记录
                    IndicatorResult result = IndicatorResult.builder()
                            .projectId(projectId)
                            .assessmentId(assessmentId)
                            .indicatorEsId(indicatorId)
                            .indicatorName(metadata != null ? metadata.name : indicatorId)
                            .indicatorLevel(metadata != null && metadata.indicatorLevel != null ? metadata.indicatorLevel : 0)
                            .dimension(metadata != null ? metadata.dimension : null)
                            .type(metadata != null ? metadata.type : null)
                            .calculatedScore(absoluteScore)
                            .maxPossibleScore(maxPossible)
                            .usedCalculationRuleType("auto")
                            .calculationDetails(IndicatorResultDetail.builder()
                                    .relatedIndicators(new ArrayList<>(relatedIndicators))
                                    .build())
                            .riskTriggered(false)
                            .riskStatus(IndicatorRiskStatus.fromCode("NOT_EVALUATED"))
                            .calculatedAt(LocalDateTime.now())
                            .createdAt(LocalDateTime.now())
                            .build();
                    
                    resultsToSave.add(result);
                }
            }
            
            // 批量保存
            if (!resultsToSave.isEmpty()) {
                log.info("[Batch Save] 准备保存 {} 个指标结果", resultsToSave.size());
                indicatorResultRepository.saveAll(resultsToSave);
                log.info("[Batch Save Success] 保存了 {} 个指标结果", resultsToSave.size());
            } else {
                log.info("[Batch Save] 无指标结果需要保存");
            }
        } catch (Exception e) {
            log.error("[Batch Save Failed] error={}", e.getMessage(), e);
            throw new IllegalStateException("Failed to save indicator results: assessmentId=" + assessmentId, e);
        }
    }

    private void updateAssessmentStatus(Long assessmentId, AssessmentStatusEnum assessmentStatus) {
        Optional<Assessment> opt = assessmentRepository.findById(assessmentId);
        if (opt.isPresent()) {
            Assessment ar = opt.get();
            ar.setStatus(assessmentStatus);
            assessmentRepository.saveAndFlush(ar);

            AssessmentStatusEnum persistedStatus = assessmentRepository.findById(assessmentId)
                    .map(Assessment::getStatus)
                    .orElse(null);
            if (persistedStatus != assessmentStatus) {
                throw new IllegalStateException("Assessment status was not persisted: assessmentId="
                        + assessmentId + ", expected=" + assessmentStatus + ", actual=" + persistedStatus);
            }
            log.info("[Assessment Status Persisted] assessmentId={}, status={}", assessmentId, persistedStatus);
        } else {
            log.warn("[Assessment Not Found] assessmentId={}", assessmentId);
            throw new RuntimeException("[Assessment Not Found] assessmentId=" + assessmentId);
        }
    }

    /**
     * 幂等地将评估标记为已完成，供计算流程和消息任务在不同边界重复确认。
     */
    public void markAssessmentCompleted(Long assessmentId) {
        updateAssessmentStatus(assessmentId, AssessmentStatusEnum.ASSESSED);
    }

    /** 在所有重试均失败后记录终态，避免评估永久停留在“评估中”。 */
    public void markAssessmentFailed(Long assessmentId) {
        updateAssessmentStatus(assessmentId, AssessmentStatusEnum.FAILED);
    }

    /** 幂等地将项目标记为已完成，并立即刷新、回查数据库。 */
    public void markProjectCompleted(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("[Project Not Found] projectId=" + projectId));
        project.setStatus(ProjectStatus.COMPLETED);
        project.setActualCompletionDate(java.time.LocalDate.now());
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.saveAndFlush(project);

        ProjectStatus persistedStatus = projectRepository.findById(projectId)
                .map(Project::getStatus)
                .orElse(null);
        if (persistedStatus != ProjectStatus.COMPLETED) {
            throw new IllegalStateException("Project status was not persisted: projectId="
                    + projectId + ", expected=COMPLETED, actual=" + persistedStatus);
        }
        log.info("[Project Status Persisted] projectId={}, status={}", projectId, persistedStatus);
    }


    private void completeAssessmentIfNeeded(Long userId, Long projectId, Long assessmentId) {
        // 行为评估计算已经完成，先在当前服务中持久化最终状态。
        // 报告服务仍会消费下面的完成事件来汇总报告，但评估状态不再依赖
        // report 服务是否在线或 Kafka 消息是否被及时消费。
        markAssessmentCompleted(assessmentId);
        markProjectCompleted(projectId);

        AssessmentCompletedEventMessage assessmentCompletedEventMessage = new AssessmentCompletedEventMessage(
                StringUtils.generateMessageId(),
                String.valueOf(System.currentTimeMillis()),
                StringUtils.generateTraceId(),
                userId,
                projectId,
                assessmentId
        );
        try {
            kafkaUtils.sendMessage(assessmentCompletedEventMessage);
        } finally {
            // 即使 Kafka 发送失败，也再次确认数据库中的最终状态。
            markAssessmentCompleted(assessmentId);
            markProjectCompleted(projectId);
        }
        log.info("[Assessment Completed] projectId={}, assessmentId={}", projectId, assessmentId);
    }

    // 改名：保留原 compute-only 方法（不入库），接收带分数的候选列表
    public DocumentProcessingResult.MappingResult computeMappingFromCandidates(Behavior behavior,
                                                                               List<Scored<Indicator>> indicators,
                                                                               List<Scored<Regulation>> regulations) {

        List<String> warnings = new ArrayList<>();

        // 存储每个指标下所有法规的详细信息：(法规得分, 层级权重, 相似度权重)
        Map<String, List<RegulationScore>> indicatorRegScores = new HashMap<>();
        Map<String, RelatedIndicator> indicatorResults = new HashMap<>();

        // 先为每个有效候选创建结果及行为证据，未匹配法规时也能返回兜底分数。
        for (Scored<Indicator> s : indicators) {
            Indicator ind = s.getItem();
            if (ind != null && ind.getId() != null) {
                indicatorRegScores.put(ind.getId(), new ArrayList<>());
                indicatorResults.put(ind.getId(), RelatedIndicator.builder()
                        .indicatorId(ind.getId())
                        .indicatorName(ind.getName())
                        .score(0.0)
                        .maxScore(ind.getMaxScore())
                        .relatedBehaviors(new ArrayList<>(Collections.singletonList(
                                RelatedBehavior.builder()
                                        .projectId(behavior.getProjectId())
                                        .description(behavior.getDescription())
                                        .relatedRegulations(new ArrayList<>())
                                        .build()
                        )))
                        .build());
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
                RelatedIndicator indicatorResult = indicatorResults.get(ind.getId());
                // 同一候选的行为证据已初始化，只追加实际匹配的法规。
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

        return DocumentProcessingResult.MappingResult.builder()
                .behaviorId(behavior.getId())
                .relatedIndicators(indicatorResults)
                .warnings(warnings)
                .build();
    }

    /**
     * 对单个法规进行定性/定量计算

     */
    private double computeRegulationScore(Behavior behavior, Regulation regulation) {

        boolean hasQuantitativeData = Objects.equals(regulation.getType(), "定量") ||Objects.equals(behavior.getType(), "定量");

        if (hasQuantitativeData) {
            // 定量计算：根据法规定量指标和行为定量数据计算
            return QuantitativeCalculator.computeQuantitativeScore(
                    regulation.getQuantitativeIndicator(),
                    behavior.getQuantitativeData(),
                    regulation.getDirection(),
                    behavior.getStatus()
            );
        } else {
            // 定性计算：根据法规方向和行为状态矩阵计算

            return QualitativeCalculator.computeQualitativeScore(
                    regulation.getDirection(),
                    behavior.getStatus()
            );
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
     * 从 ES 中获取指定 projectId 的所有 behaviors
     * 修改：处理所有行为，不再随机选择
     */
    private List<Behavior> fetchRandomBehaviors( Long projectId) {
        try {
            // 修改：使用 scroll API 或更大的 size 来获取所有行为
            // 这里先设置一个较大的值，实际项目中可能需要使用 scroll API
            int fetchSize = 10000;

            SearchResponse<Behavior> resp = esClient.search(s -> s
                            .index(ElasticSearchConfig.BEHAVIOR_INDEX)
                            .size(fetchSize)
                            .query(q -> q.bool(ma -> ma.must(m1 ->m1.term(t->t.field("project_id").value(projectId))))),
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

            // 修改：处理所有行为，不再随机选择
            List<Behavior> selectedBehaviors = new ArrayList<>();
            if (!allBehaviors.isEmpty()) {
                // 注释掉随机排序逻辑
                // Collections.shuffle(allBehaviors, new Random(System.currentTimeMillis()));
                // int selectCount = Math.min(count, allBehaviors.size());

                // 处理所有行为
                for (Behavior behavior : allBehaviors) {
                    behavior.setProjectId(projectId);
                    selectedBehaviors.add(behavior);
                }
            }

            log.info("[Fetch Behaviors] projectId={}, totalCount={}, selectedCount={}", 
                    projectId, allBehaviors.size(), selectedBehaviors.size());

            return selectedBehaviors;

        } catch (Exception e) {
            log.error("[Fetch Behaviors Failed] error={}", e.getMessage());
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


}
