package com.riskwarning.report.controller;

import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.result.Result;
import com.riskwarning.report.repository.AssessmentRepository;
import com.riskwarning.report.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/report")
public class ReportController {


    @Autowired
    private ReportService reportService;

    @Autowired
    private AssessmentRepository assessmentRepository;


    @GetMapping("/report/indicatorResult/{assessmentId}")
    public Result reportIndicatorResult(@PathVariable Long assessmentId) {
        Assessment assessment=assessmentRepository.findById(assessmentId).orElse(null);
        return Result.success(reportService.assembleIndicatorResult(assessment));
    }

    @GetMapping("/report/risk")
    public Result reportRisk(@RequestParam Long assessmentId, @RequestParam (required = false) String dimension, @RequestParam(required = false) String riskLevel) {
        return Result.success(reportService.assembleRisk(assessmentId, dimension, riskLevel));
    }

    @GetMapping("/report/general/{assessmentId}")
    public Result reportGeneral(@PathVariable Long assessmentId) {
        Assessment assessment=assessmentRepository.findById(assessmentId).orElse(null);
        return Result.success(reportService.assembleGeneral(assessment));
    }

}
