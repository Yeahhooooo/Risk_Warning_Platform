package com.riskwarning.report.controller;

import com.riskwarning.common.hardcode.FixedAssessmentData;
import com.riskwarning.common.result.Result;
import com.riskwarning.report.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 评估结果查询接口。
 *
 * <p>当前处于演示阶段：general / indicatorResult / risk 三个接口**无条件**返回
 * 《JUDGE-A4DF00BA94F3 合规判定报告》写死的评估结果，请求参数只用于兼容原有调用方式，
 * 不参与任何数据查询（详见 {@link FixedAssessmentData}）。
 */
@RestController
@RequestMapping("/report")
public class ReportController {


    @Autowired
    private ReportService reportService;


    @GetMapping({"/report/indicatorResult/{assessmentId}", "/report/indicatorResult"})
    public Result reportIndicatorResult(@PathVariable(required = false) Long assessmentId) {
        return Result.success(reportService.assembleIndicatorResult(FixedAssessmentData.assessment()));
    }

    @GetMapping("/report/risk")
    public Result reportRisk(@RequestParam(required = false) Long assessmentId) {
        return Result.success(reportService.assembleRisk(FixedAssessmentData.ASSESSMENT_ID));
    }

    @GetMapping({"/report/general/{assessmentId}", "/report/general"})
    public Result reportGeneral(@PathVariable(required = false) Long assessmentId) {
        return Result.success(reportService.assembleGeneral(FixedAssessmentData.assessment()));
    }

}
