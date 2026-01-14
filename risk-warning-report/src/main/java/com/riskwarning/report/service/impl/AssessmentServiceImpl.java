package com.riskwarning.report.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.config.ElasticSearchConfig;
import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.common.enums.risk.RiskLevelEnum;
import com.riskwarning.common.enums.risk.RiskStatusEnum;
import com.riskwarning.common.po.behavior.Behavior;
import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.po.risk.RelatedIndicator;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    private final static Double THRESHOLD_RATIO = 0.5;

    @Override
    public void aggregateInformation(Long userId, Long projectId, Long assessmentId) {
        Assessment assessment = assessmentRepository.findById(assessmentId).orElse(null);
        if (assessment == null) {
            throw new RuntimeException("Assessment not found");
        }
        // todo：查询IndicatorResult表，获取所有指标结果，计算结果小于maxScore * 0.5的产生风险，预留处置接口
        List<IndicatorResult> indicatorResults = indicatorResultRepository.findByAssessmentId(assessmentId);
        List<Risk> risks = new ArrayList<>();
        double totalScore = 0.0;
        int lowRiskCount = 0, mediumRiskCount = 0, highRiskCount = 0, totalRiskCount = 0;
        for(IndicatorResult ir : indicatorResults){
            double calculatedScore = ir.getCalculatedScore().doubleValue();
            double maxScore = ir.getMaxPossibleScore().doubleValue() == 0.0 ? calculatedScore : ir.getMaxPossibleScore().doubleValue();
            totalScore += calculatedScore;
            double scoreRatio = calculatedScore / maxScore;
            if(scoreRatio < THRESHOLD_RATIO) {
                totalRiskCount++;
                RiskLevelEnum riskLevelEnum = RiskLevelEnum.getByScoreRatio(scoreRatio);
                lowRiskCount += riskLevelEnum == RiskLevelEnum.LOW_RISK ? 1 : 0;
                mediumRiskCount += riskLevelEnum == RiskLevelEnum.MEDIUM_RISK ? 1 : 0;
                highRiskCount += riskLevelEnum == RiskLevelEnum.HIGH_RISK ? 1 : 0;
                Risk risk = Risk.builder()
                        .projectId(projectId)
                        .assessmentId(assessmentId)
                        .name("Risk from Indicator " + ir.getIndicatorEsId())
                        .dimension(ir.getDimension())
                        .description("")
                        .riskLevel(riskLevelEnum)
                        .probability(0.0)
                        .impact(0.0)
                        .detectability(0.0)
                        .status(RiskStatusEnum.TO_BE_DISPOSED)
                        .responsibleParty("")
                        .affectedObjects(new String[]{})
                        .impactScope("")
                        .countermeasures("")
                        .relatedIndicators(ir.getCalculationDetails().getRelatedIndicators())
                        .createAt(LocalDateTime.now())
                        .build();
                risks.add(risk);
            }
        }

        try {
            BulkResponse response = elasticsearchClient.bulk(b -> {
                for (Risk risk : risks) {
                    b.operations(op -> op
                            .index(idx -> idx
                                    .index(ElasticSearchConfig.RISK_INDEX)
                                    .document(risk)
                            )
                    );
                }
                return b;
            });
            if (response.errors()) {
                log.error("Bulk insert encountered errors: {}", response.items().toString());
                throw new Exception("Bulk insert to Elasticsearch failed");
            }
        } catch (Exception e) {
            log.error("Failed to index risk documents to Elasticsearch: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        assessment.setOverallScore(totalScore);
//            // TODO: 总风险等级依靠owRiskCount, mediumRiskCoun, highRiskCount来设置
        assessment.setOverallRiskLevel(RiskLevelEnum.getByRiskCount(lowRiskCount, mediumRiskCount, highRiskCount));
        assessment.setStatus(AssessmentStatusEnum.ASSESSED);
        assessment.setDetails(JSON.toJSONString(new AssessmentGeneralDetails(
                reportService.assembleGeneral(assessment),
                reportService.assembleIndicatorResult(assessment)
        )));
        assessment.setAssessmentDate(LocalDateTime.now());
        assessmentRepository.save(assessment);
    }
}
