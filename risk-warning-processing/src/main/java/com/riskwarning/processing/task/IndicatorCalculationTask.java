package com.riskwarning.processing.task;

import com.riskwarning.common.message.IndicatorCalculationTaskMessage;
import com.riskwarning.processing.service.BehaviorProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 指标计算任务消费者
 * 监听 indicator_calculation_tasks topic，执行指标计算和存储
 *
 * ✅ Kafka listener 不包事务
 * ✅ processProjectBehaviors 内部自己管理事务
 */
@Component
@Slf4j
public class IndicatorCalculationTask {

    @Autowired
    private BehaviorProcessingService behaviorProcessingService;

    @KafkaListener(topics = "indicator_calculation_tasks", groupId = "indicator-calculation-consumer")
    public void onMessage(IndicatorCalculationTaskMessage message) {
        log.info("========================================");
        log.info("[Kafka消息接收] topic=indicator_calculation_tasks, messageId={}, projectId={}, assessmentId={}",
                message.getMessageId(), message.getProjectId(), message.getAssessmentId());
        log.info("========================================");

        if (message == null || message.getProjectId() == null || message.getAssessmentId() == null) {
            log.error("[CRITICAL] 消息参数不完整: message={}", message);
            return;
        }

        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│  开始指标计算任务                                           │");
        log.info("│  Project ID: {}", String.format("%-45s", message.getProjectId()) + "│");
        log.info("│  Assessment ID: {}", String.format("%-42s", message.getAssessmentId()) + "│");
        log.info("└─────────────────────────────────────────────────────────────┘");

        long startTime = System.currentTimeMillis();

        try {
            // ✅ 直接调用，不包事务
            // ✅ processProjectBehaviors 内部自己管理事务
            log.info("▶ 开始行为评估和指标计算...");
            behaviorProcessingService.processProjectBehaviors(
                    message.getProjectId(),
                    message.getAssessmentId()
            );
            log.info("✓ 行为评估和指标计算完成");

            long duration = System.currentTimeMillis() - startTime;
            log.info("┌─────────────────────────────────────────────────────────────┐");
            log.info("│  ✓ 指标计算任务执行成功                                     │");
            log.info("│  Project ID: {}", String.format("%-45s", message.getProjectId()) + "│");
            log.info("│  Assessment ID: {}", String.format("%-42s", message.getAssessmentId()) + "│");
            log.info("│  总耗时: {}", String.format("%-50s", duration + "ms") + "│");
            log.info("└─────────────────────────────────────────────────────────────┘");

        } catch (Exception e) {
            log.error("✗ 指标计算任务失败: projectId={}, assessmentId={}, error={}",
                    message.getProjectId(), message.getAssessmentId(), e.getMessage(), e);

            // 打印 root cause
            Throwable rootCause = e;
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            log.error("✗ [Root Cause] {}: {}",
                    rootCause.getClass().getName(),
                    rootCause.getMessage());

            throw new RuntimeException("指标计算任务失败", e);
        }
    }
}

