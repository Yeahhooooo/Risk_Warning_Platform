package com.riskwarning.processing.controller;

import com.riskwarning.common.po.behavior.Behavior;
import com.riskwarning.common.result.Result;
import com.riskwarning.processing.dto.MappingResult;
import com.riskwarning.processing.service.BehaviorProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 行为处理控制器
 *
 * 提供一个简单的入口，用于触发 BehaviorProcessingService 的计算并持久化到数据库。
 */
@Slf4j
@RestController
@RequestMapping("/api/behavior")
@RequiredArgsConstructor
public class BehaviorProcessingController {

    private final BehaviorProcessingService behaviorProcessingService;

    /**
     * 接收一个行为（Behavior），调用服务进行指标评估并写入数据库。
     *
     * 注意：Behavior 中必须包含有效的 projectId，否则服务会抛出 IllegalArgumentException。
     */
    @PostMapping("/process")
    public Result<MappingResult> process(@RequestBody Behavior behavior) {
        try {
            MappingResult result = behaviorProcessingService.processAndPersistFromBehavior(behavior);
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            // 业务参数错误
            log.warn("处理行为时参数错误: {}", e.getMessage());
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            // 其他未知错误
            log.error("处理行为失败", e);
            return Result.fail("处理行为失败: " + e.getMessage());
        }
    }
}

