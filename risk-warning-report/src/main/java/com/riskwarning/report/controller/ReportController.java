package com.riskwarning.report.controller;

import com.riskwarning.common.hardcode.FixedAssessmentData;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.result.Result;
import com.riskwarning.report.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 评估结果查询接口。
 *
 * <p>演示阶段：general / indicatorResult / risk 三个接口<b>完全忽略请求参数</b>，
 * 恒定返回《JUDGE-A4DF00BA94F3 合规判定报告》对应的写死评估结论
 * （{@link FixedAssessmentData#assessment()}，其中 assessmentId / projectId 恒为 900001）。</p>
 *
 * <p>因此这里不查数据库、不查 ES：传入任意 assessmentId（真实评估记录 id、不存在的 id、
 * 甚至不传）得到的响应体都完全相同。</p>
 *
 * <p>路径变量与查询参数仅为保持前端调用地址不变而保留，取值不参与取数。</p>
 */
@RestController
@RequestMapping("/report")
public class ReportController {


    @Autowired
    private ReportService reportService;


    @GetMapping({"/report/indicatorResult/{assessmentId}", "/report/indicatorResult"})
    public Result reportIndicatorResult(@PathVariable(required = false) Long assessmentId) {
        return Result.success(reportService.assembleIndicatorResult(fixedAssessment()));
    }

    @GetMapping("/report/risk")
    public Result reportRisk(@RequestParam(required = false) Long assessmentId) {
        return Result.success(reportService.assembleRisk(FixedAssessmentData.ASSESSMENT_ID));
    }

    @GetMapping({"/report/general/{assessmentId}", "/report/general"})
    public Result reportGeneral(@PathVariable(required = false) Long assessmentId) {
        return Result.success(reportService.assembleGeneral(fixedAssessment()));
    }

    /** 恒定返回写死的演示评估记录，请求参数不参与取数。 */
    private Assessment fixedAssessment() {
        return FixedAssessmentData.assessment();
    }

}
