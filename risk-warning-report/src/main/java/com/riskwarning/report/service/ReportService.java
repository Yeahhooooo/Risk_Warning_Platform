package com.riskwarning.report.service;

import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.report.entity.vo.general.AssessmentDetailVO;
import com.riskwarning.report.entity.vo.indicator.IndicatorDistributionVO;
import com.riskwarning.report.entity.vo.risk.RiskVO;

import java.util.List;

public interface ReportService {

    IndicatorDistributionVO assembleIndicatorResult(Assessment assessment);

    List<RiskVO> assembleRisk(Long assessmentId, String dimension, String riskLevel);

    AssessmentDetailVO assembleGeneral(Assessment assessment);
}
