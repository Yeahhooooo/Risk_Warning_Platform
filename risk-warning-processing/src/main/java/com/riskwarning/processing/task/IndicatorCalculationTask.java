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


}

