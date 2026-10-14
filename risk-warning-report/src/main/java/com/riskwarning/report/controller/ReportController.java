package com.riskwarning.report.controller;

import com.riskwarning.common.utils.AssessmentDataGuard;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.result.Result;
import com.riskwarning.report.service.ReportService;
import com.riskwarning.report.repository.AssessmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 评估结果查询接口。
 *
 * 按请求的评估 ID 查询持久化结果。
 */
@RestController
@RequestMapping("/report")
public class ReportController {


    @Autowired
    private ReportService reportService;

    @Autowired
    private AssessmentRepository assessmentRepository;


    @GetMapping("/report/indicatorResult/{assessmentId}")
    public Result reportIndicatorResult(@PathVariable Long assessmentId) {
        return Result.success(reportService.assembleIndicatorResult(findAssessment(assessmentId)));
    }

    @GetMapping("/report/risk")
    public Result reportRisk(@RequestParam Long assessmentId) {
        findAssessment(assessmentId);
        return Result.success(reportService.assembleRisk(assessmentId));
    }

    @GetMapping("/report/general/{assessmentId}")
    public Result reportGeneral(@PathVariable Long assessmentId) {
        return Result.success(reportService.assembleGeneral(findAssessment(assessmentId)));
    }

    private Assessment findAssessment(Long assessmentId) {
        return AssessmentDataGuard.requireReal(assessmentRepository.findById(assessmentId).orElse(null));
    }

}
