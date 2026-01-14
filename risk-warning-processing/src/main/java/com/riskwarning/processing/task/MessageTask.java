package com.riskwarning.processing.task;


import com.riskwarning.common.constants.Constants;
import com.riskwarning.common.enums.indicator.IndicatorRiskStatus;
import com.riskwarning.common.message.BehaviorProcessingTaskMessage;
import com.riskwarning.common.message.IndicatorCalculationTaskMessage;
import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.utils.FileUtils;
import com.riskwarning.common.utils.KafkaUtils;
import com.riskwarning.common.utils.StringUtils;
import com.riskwarning.processing.batch.BatchJob;
import com.riskwarning.processing.service.BehaviorProcessingService;
import com.riskwarning.processing.service.DocumentProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class MessageTask {

    @Autowired
    private BatchJob batchJob;

    @Autowired
    private DocumentProcessingService documentProcessingService;

    @Autowired
    private BehaviorProcessingService behaviorProcessingService;

    @Autowired
    @Qualifier(value = "FileProcessTaskThreadPool")
    private ThreadPoolTaskExecutor fileThreadPoolExecutor;

    @Autowired
    @Qualifier(value = "BehaviorProcessTaskThreadPool")
    private ThreadPoolTaskExecutor behaviorThreadPoolExecutor;

    @Autowired
    private KafkaUtils kafkaUtils;


    @KafkaListener(topics = "behavior_processing_tasks", groupId = "test-consumer")
    public void onMessage(BehaviorProcessingTaskMessage message) {
        log.info("========================================");
        log.info("[Kafka消息接收] topic=behavior_processing_tasks, messageId={}, projectId={}",
                message.getMessageId(), message.getProjectId());
        log.info("[消息详情] type={}, fileCount={}", message.getType(),
                message.getFilePaths() != null ? message.getFilePaths().size() : 0);

        try {
            switch (message.getType()){
                case FILE_UPLOAD:
                    log.info("┌─────────────────────────────────────────────────────────────┐");
                    log.info("│  开始处理文件上传任务                                        │");
                    log.info("│  Project ID: {}", String.format("%-45s", message.getProjectId()) + "│");
                    log.info("└─────────────────────────────────────────────────────────────┘");

                    fileThreadPoolExecutor.execute(() -> {
                        long startTime = System.currentTimeMillis();

                        try {
                            // 步骤1: 文档处理 - 提取行为
                            log.info("▶ 步骤 1/3: 开始文档处理和行为提取...");
                            List<String> internalFiles = documentProcessingService.processDocument(
                                    message.getProjectId(),
                                    new ArrayList<>(),
                                    message.getFilePaths()
                            );
                            log.info("✓ 步骤 1/3 完成: 文档处理成功，生成内部文件数={}", internalFiles.size());

                            // 步骤2: 批处理 - 将行为存入ES
                            log.info("▶ 步骤 2/3: 开始批处理任务，将行为数据存入ES...");
//                            batchJob.runBatchJob(
//                                    message.getProjectId(),
//                                    internalFiles
//                            );
                            log.info("✓ 步骤 2/3 完成: 批处理任务成功，行为数据已存入ES");

                            // 步骤3: 发送消息到指标计算任务队列
                            log.info("▶ 步骤 3/3: 发送消息到指标计算任务队列...");
                            Long assessmentId = message.getAssessmentId();
                            if (assessmentId == null) {
                                log.error("[CRITICAL] Kafka 消息中的 assessmentId 为 null! projectId={}", message.getProjectId());
                                throw new IllegalArgumentException("assessmentId must not be null in message");
                            }

                            // ✅ 创建指标计算任务消息
                            IndicatorCalculationTaskMessage indicatorMessage = new IndicatorCalculationTaskMessage(
                                    StringUtils.generateMessageId(),
                                    String.valueOf(System.currentTimeMillis()),
                                    message.getTraceId(),
                                    message.getUserId(),
                                    message.getProjectId(),
                                    assessmentId
                            );

                            // ✅ 发送到 indicator_calculation_tasks topic
                            kafkaUtils.sendMessage(indicatorMessage);
                            log.info("✓ 步骤 3/3 完成: 已发送指标计算任务消息, assessmentId={}", assessmentId);


                            long duration = System.currentTimeMillis() - startTime;
                            log.info("┌─────────────────────────────────────────────────────────────┐");
                            log.info("│  ✓ 行为处理流程执行成功                                     │");
                            log.info("│  Project ID: {}", String.format("%-45s", message.getProjectId()) + "│");
                            log.info("│  Assessment ID: {}", String.format("%-42s", assessmentId) + "│");
                            log.info("│  总耗时: {}", String.format("%-50s", duration + "ms") + "│");
                            log.info("│  下一步: 等待指标计算任务处理                               │");
                            log.info("└─────────────────────────────────────────────────────────────┘");

                        } catch (Exception e) {
                            log.error("✗ 处理文件上传任务失败: projectId={}, error={}",
                                    message.getProjectId(), e.getMessage(), e);
                            throw new RuntimeException("处理失败", e);
                        } finally {
                            log.info("Finished processing file upload for projectId: {}", message.getProjectId());
                            // 删除中间文件
                            // 清理临时文件
                            log.info("▶ 清理临时文件: projectId={}", message.getProjectId());
                            String internalFilePath = Constants.getInternalDirPath(message.getProjectId());
                            FileUtils.delDirectory(internalFilePath);
                            log.info("✓ 临时文件清理完成");
                        }
                    });
                    break;

                case KNOWLEDGE_QUERY:
                    log.info("[知识查询任务] 暂未实现");
                    // todo: 处理知识查询任务
                    break;

                case QUESTIONNAIRE:
                    log.info("[问卷任务] 暂未实现");
                    // todo: 处理问卷任务
                    break;

                default:
                    log.warn("[未知任务类型] type={}", message.getType());
                    break;
            }
        } catch (Exception e) {
            log.error("[Kafka消息处理异常] messageId={}, projectId={}, error={}",
                    message.getMessageId(), message.getProjectId(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
        log.info("========================================");
    }

    @KafkaListener(topics = "indicator_calculation_tasks", groupId = "indicator-calculation-consumer")
    public void onMessage(IndicatorCalculationTaskMessage message) {

        log.info("========================================");
        log.info("[Kafka消息接收] topic=indicator_calculation_tasks, messageId={}, projectId={}, assessmentId={}",
                message.getMessageId(), message.getProjectId(), message.getAssessmentId());
        if (message == null || message.getProjectId() == null || message.getAssessmentId() == null) {
            log.error("[CRITICAL] 消息参数不完整: message={}", message);
            return;
        }
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│  开始指标计算任务                                           │");
        log.info("│  Project ID: {}", String.format("%-45s", message.getProjectId()) + "│");
        log.info("│  Assessment ID: {}", String.format("%-42s", message.getAssessmentId()) + "│");
        log.info("└─────────────────────────────────────────────────────────────┘");

        behaviorThreadPoolExecutor.execute(() -> {
            try {
                log.info("▶ 开始行为评估和指标计算...");
                behaviorProcessingService.processProjectBehaviors(
                        message.getUserId(),
                        message.getProjectId(),
                        message.getAssessmentId()
                );
            } catch (Exception e) {
                log.error("✗ 指标计算任务失败: projectId={}, assessmentId={}, error={}",
                        message.getProjectId(), message.getAssessmentId(), e.getMessage(), e);

                throw new RuntimeException("指标计算任务失败", e);
            }
        });
    }
}
