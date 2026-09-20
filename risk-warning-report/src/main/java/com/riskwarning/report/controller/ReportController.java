package com.riskwarning.report.controller;

import com.riskwarning.common.hardcode.FixedAssessmentData;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.result.Result;
import com.riskwarning.report.repository.AssessmentRepository;
import com.riskwarning.report.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 评估结果查询接口。
 *
 * <p>当前处于演示阶段：general / indicatorResult / risk 三个接口返回
 * 《JUDGE-A4DF00BA94F3 合规判定报告》的固定评估结论（详见 {@link FixedAssessmentData}）。</p>
 *
 * <p>传入了 assessmentId 时，会先按 id 回查评估记录：</p>
 * <ul>
 *   <li>记录存在（例如 org 模拟评估流程产生的真实记录）→ 使用该记录，
 *       使返回内容中的 assessmentId / projectId / 评估时间与本次触发一致；</li>
 *   <li>记录不存在（例如前端仍传 900001 或未传参）→ 回退到写死的演示评估，保证接口永远有数据。</li>
 * </ul>
 */
@RestController
@RequestMapping("/report")
public class ReportController {


    @Autowired
    private ReportService reportService;

    @Autowired
    private AssessmentRepository assessmentRepository;


    @GetMapping({"/report/indicatorResult/{assessmentId}", "/report/indicatorResult"})
    public Result reportIndicatorResult(@PathVariable(required = false) Long assessmentId) {
        return Result.success(reportService.assembleIndicatorResult(resolve(assessmentId)));
    }

    @GetMapping("/report/risk")
    public Result reportRisk(@RequestParam(required = false) Long assessmentId) {
        return Result.success(reportService.assembleRisk(resolve(assessmentId).getId()));
    }

    @GetMapping({"/report/general/{assessmentId}", "/report/general"})
    public Result reportGeneral(@PathVariable(required = false) Long assessmentId) {
        return Result.success(reportService.assembleGeneral(resolve(assessmentId)));
    }

    /** 优先使用请求指定的真实评估记录，查不到则回退到写死的演示评估数据。 */
    private Assessment resolve(Long assessmentId) {
        if (assessmentId != null && assessmentRepository != null) {
            Assessment stored = assessmentRepository.findById(assessmentId).orElse(null);
            if (stored != null) {
                return stored;
            }
        }
        return FixedAssessmentData.assessment();
    }

}
