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

//    /**
//     * 接收一个行为（Behavior），调用服务进行指标评估并写入数据库。
//     *
//     * 注意：Behavior 中必须包含有效的 projectId，否则服务会抛出 IllegalArgumentException。
//     */
//    @PostMapping("/process")
//    public Result<MappingResult> process(@RequestBody Behavior behavior) {
//        try {
//            MappingResult result = behaviorProcessingService.processAndPersistFromBehavior(behavior);
//            return Result.success(result);
//        } catch (IllegalArgumentException e) {
//            // 业务参数错误
//            log.warn("处理行为时参数错误: {}", e.getMessage());
//            return Result.fail(400, e.getMessage());
//        } catch (Exception e) {
//            // 其他未知错误
//            log.error("处理行为失败", e);
//            return Result.fail("处理行为失败: " + e.getMessage());
//        }
//    }

    /**
     * 新接口：根据 projectId 处理该项目的所有 behaviors
     *
     * 1. 从 ES 获取该项目的所有 behaviors
     * 2. 创建一个 assessment 记录
     * 3. 依次处理每个 behavior 并计算指标
     * 4. 将结果保存到数据库
     *
     * @param projectId 项目ID
     * @return 聚合的评估结果
     */
    @PostMapping("/process-project/{projectId}")
    public Result<MappingResult> processProject(@PathVariable Long projectId) {
        try {
            log.info("开始处理项目的所有行为: projectId={}", projectId);
            MappingResult result = behaviorProcessingService.processProjectBehaviors(projectId);
            log.info("项目处理完成: projectId={}, 影响指标数={}", projectId,
                    result.getIndicatorScores() != null ? result.getIndicatorScores().size() : 0);
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            log.warn("处理项目行为时参数错误: projectId={}, error={}", projectId, e.getMessage());
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("处理项目行为失败: projectId={}", projectId, e);
            return Result.fail("处理项目行为失败: " + e.getMessage());
        }
    }
}

