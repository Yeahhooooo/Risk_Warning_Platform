package com.riskwarning.report.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.common.enums.RiskLevelEnum;
import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.po.risk.Risk;
import com.riskwarning.report.entity.vo.AssessmentGeneralDetails;
import com.riskwarning.report.repository.AssessmentRepository;
import com.riskwarning.report.repository.IndicatorResultRepository;
import com.riskwarning.report.service.AssessmentService;
import com.riskwarning.report.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class AssessmentServiceImpl implements AssessmentService {

    @Autowired
    private ReportService reportService;

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private IndicatorResultRepository indicatorResultRepository;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Override
    public void aggregateInformation(Long userId, Long projectId, Long assessmentId) {
        Assessment assessment = assessmentRepository.findById(assessmentId).orElse(null);
        if (assessment == null) {
            throw new RuntimeException("Assessment not found");
        }
        // todo：查询IndicatorResult表，获取所有指标结果，计算结果小于maxScore * 0.5的产生风险，预留处置接口
        List<IndicatorResult> indicatorResults = indicatorResultRepository.findByAssessmentId(assessmentId);
        BigDecimal totalScore = BigDecimal.ZERO;
        int lowRiskCount = 0, mediumRiskCount = 0, highRiskCount = 0;
        for(IndicatorResult ir : indicatorResults){
            totalScore.add(ir.getCalculatedScore());
            if(ir.getCalculatedScore().compareTo(ir.getMaxPossibleScore().multiply(new BigDecimal("0.5"))) < 0){
//                Risk risk = new Risk();
//                try {
//                    elasticsearchClient.index(builder -> builder
//                            .index("risks")
//                            .document(risk)
//                    );
//                } catch (IOException e) {
//                    log.error("Failed to index risk for indicatorResultId: {}", ir.getId(), e);
//                    throw new RuntimeException(e);
//                }
            }
//            assessment.setOverallScore(totalScore.doubleValue());
//
//            // TODO: 总风险等级依靠owRiskCount, mediumRiskCoun, highRiskCount来设置
//            assessment.setOverallRiskLevel(RiskLevelEnum.LOW_RISK);
//            assessment.setStatus(AssessmentStatusEnum.ASSESSED);
//            assessment.setDetails(JSON.toJSONString(new AssessmentGeneralDetails(
//                    reportService.assembleGeneral(assessmentId),
//                    reportService.assembleIndicatorResult(assessmentId)
//            )));
//            assessment.setAssessmentDate(LocalDateTime.now());
//            assessmentRepository.save(assessment);
        }
    }
}
