package com.riskwarning.report.controller;

import com.riskwarning.common.result.Result;
import com.riskwarning.report.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/report")
public class ReportController {


    @Autowired
    private ReportService reportService;


    @GetMapping("/report/indicatorResult/{assessmentId}")
    public Result reportIndicatorResult(@PathVariable Long assessmentId) {
        return Result.success(reportService.assembleIndicatorResult(assessmentId));
    }

    @GetMapping("/report/risk")
    public Result reportRisk(@RequestParam Long assessmentId, @RequestParam (required = false) String dimension, @RequestParam(required = false) String riskLevel) {
        return Result.success(reportService.assembleRisk(assessmentId, dimension, riskLevel));
    }

    @GetMapping("/report/general/{assessmentId}")
    public Result reportGeneral(@PathVariable Long assessmentId) {
        return Result.success(reportService.assembleGeneral(assessmentId));
    }

}
