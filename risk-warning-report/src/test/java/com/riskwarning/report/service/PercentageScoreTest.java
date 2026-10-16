package com.riskwarning.report.service;

import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.enums.RiskDimensionEnum;
import com.riskwarning.common.enums.risk.RiskLevelEnum;
import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.po.project.Project;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.utils.KafkaUtils;
import com.riskwarning.report.entity.vo.AssessmentGeneralDetails;
import com.riskwarning.report.entity.vo.general.*;
import com.riskwarning.report.entity.vo.indicator.IndicatorDistributionVO;
import com.riskwarning.report.repository.*;
import com.riskwarning.report.service.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PercentageScoreTest {
    private final IndicatorResultRepository indicators = mock(IndicatorResultRepository.class);
    private final ReportServiceImpl report = new ReportServiceImpl();
    private final Assessment assessment = Assessment.builder().id(1L).projectId(2L).overallScore(85.0)
            .overallRiskLevel(RiskLevelEnum.LOW_RISK).build();

    PercentageScoreTest() { ReflectionTestUtils.setField(report, "indicatorResultRepository", indicators); }

    private IndicatorResult indicator(double score, double max, RiskDimensionEnum dimension) {
        return IndicatorResult.builder().indicatorEsId(UUID.randomUUID().toString()).indicatorName("test")
                .calculatedScore(score).maxPossibleScore(max).dimension(dimension.getDescription()).riskTriggered(false).build();
    }

    @Test void totalAndDimensionsUsePercentageAndDoNotGrowWithIndicatorCount() {
        List<IndicatorResult> rows = Arrays.asList(indicator(80,100,RiskDimensionEnum.PRODUCT_LEGITIMACY_RISK),
                indicator(5,10,RiskDimensionEnum.LABOR_LEGITIMACY_RISK));
        when(indicators.findByAssessmentId(1L)).thenReturn(rows);
        IndicatorDistributionVO result = report.assembleIndicatorResult(assessment);
        assertEquals(77.27, result.getTotalScore());
        assertEquals(80.0, result.getDimensionDistributions().get(RiskDimensionEnum.PRODUCT_LEGITIMACY_RISK).getTotalScore());
        assertEquals(50.0, result.getDimensionDistributions().get(RiskDimensionEnum.LABOR_LEGITIMACY_RISK).getTotalScore());
        when(indicators.findByAssessmentId(1L)).thenReturn(Arrays.asList(rows.get(0), rows.get(1), rows.get(0), rows.get(1)));
        assertEquals(77.27, report.assembleIndicatorResult(assessment).getTotalScore());
    }

    @Test void cachedLegacyReportsCannotReturnAccumulatedScores() {
        when(indicators.findByAssessmentId(1L)).thenReturn(Collections.singletonList(indicator(8,10,RiskDimensionEnum.PRODUCT_LEGITIMACY_RISK)));
        assessment.setOverallRiskLevel(RiskLevelEnum.MEDIUM_RISK);
        AssessmentDetailVO general = new AssessmentDetailVO();
        general.setOverallResult(OverallResult.builder().overallScore(888.0).overallRiskLevel(RiskLevelEnum.HIGH_RISK).build());
        assessment.setDetails(JSON.toJSONString(new AssessmentGeneralDetails(general,
                IndicatorDistributionVO.builder().totalScore(888.0).build())));
        assertEquals(80.0, report.assembleIndicatorResult(assessment).getTotalScore());
        assertEquals(80.0, report.assembleGeneral(assessment).getOverallResult().getOverallScore());
        assertEquals(RiskLevelEnum.MEDIUM_RISK, report.assembleGeneral(assessment).getOverallResult().getOverallRiskLevel());
    }

    @Test void zeroMaximumDoesNotProduceNaNOrFullMarks() {
        when(indicators.findByAssessmentId(1L)).thenReturn(Collections.singletonList(indicator(0,0,RiskDimensionEnum.PRODUCT_LEGITIMACY_RISK)));
        assertNull(report.assembleIndicatorResult(assessment).getTotalScore());
    }

    @Test @SuppressWarnings("unchecked") void riskEvidenceScoresArePercentagesWithoutChangingSourceRecords() throws Exception {
        com.riskwarning.common.po.risk.RelatedIndicator evidence = com.riskwarning.common.po.risk.RelatedIndicator.builder()
                .indicatorId("indicator-1").score(0.35).maxScore(10.0).build();
        com.riskwarning.common.po.risk.Risk risk = com.riskwarning.common.po.risk.Risk.builder()
                .relatedIndicators(Collections.singletonList(evidence)).riskLevel(RiskLevelEnum.MEDIUM_RISK).build();
        co.elastic.clients.elasticsearch.ElasticsearchClient es = mock(co.elastic.clients.elasticsearch.ElasticsearchClient.class);
        co.elastic.clients.elasticsearch.core.SearchResponse<com.riskwarning.common.po.risk.Risk> response =
                new co.elastic.clients.elasticsearch.core.SearchResponse.Builder<com.riskwarning.common.po.risk.Risk>()
                        .took(1).timedOut(false).shards(s -> s.total(1).successful(1).failed(0))
                        .hits(h -> h.hits(hit -> hit.index("risk").id("risk-1").source(risk))).build();
        when(es.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(com.riskwarning.common.po.risk.Risk.class))).thenReturn(response);
        ReflectionTestUtils.setField(report, "esClient", es);
        IndicatorResult row = indicator(3.5,10,RiskDimensionEnum.PRODUCT_LEGITIMACY_RISK);
        row.setIndicatorEsId("indicator-1");
        when(indicators.findByAssessmentId(1L)).thenReturn(Collections.singletonList(row));
        com.riskwarning.report.entity.vo.risk.RiskVO result = report.assembleRisk(1L).get(0);
        assertEquals(35.0, result.getRelatedIndicators().get(0).getScore());
        assertEquals(100.0, result.getRelatedIndicators().get(0).getMaxScore());
        assertEquals("MEDIUM_RISK", result.getRiskLevel());
        assertEquals(35.0, result.getScore());
        assertEquals(100.0, result.getMaxScore());
        assertEquals(0.35, evidence.getScore());
    }

    @Test void indicatorDetailsKeepExistingRiskThresholds() {
        when(indicators.findByAssessmentId(1L)).thenReturn(Arrays.asList(
                indicator(4,10,RiskDimensionEnum.PRODUCT_LEGITIMACY_RISK),
                indicator(2,10,RiskDimensionEnum.PRODUCT_LEGITIMACY_RISK),
                indicator(1.9,10,RiskDimensionEnum.PRODUCT_LEGITIMACY_RISK)));
        IndicatorDistributionVO result = report.assembleIndicatorResult(assessment);
        assertEquals(40.0, result.getIndicatorScores().get(0).getScore());
        assertEquals(RiskLevelEnum.LOW_RISK, result.getIndicatorScores().get(0).getRiskLevel());
        assertEquals(RiskLevelEnum.MEDIUM_RISK, result.getIndicatorScores().get(1).getRiskLevel());
        assertEquals(RiskLevelEnum.HIGH_RISK, result.getIndicatorScores().get(2).getRiskLevel());
        assertEquals(3, result.getDimensionDistributions().get(RiskDimensionEnum.PRODUCT_LEGITIMACY_RISK)
                .getScoreDistributions().stream().mapToInt(bucket -> bucket.getTotalCount()).sum());
    }

    @Test void persistedOverallScoreUsesTheSamePercentage() {
        when(indicators.findByAssessmentId(1L)).thenReturn(Arrays.asList(
                indicator(80,100,RiskDimensionEnum.PRODUCT_LEGITIMACY_RISK), indicator(5,10,RiskDimensionEnum.LABOR_LEGITIMACY_RISK)));
        AssessmentRepository assessments = mock(AssessmentRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        EntityManager em = mock(EntityManager.class);
        when(em.find(Assessment.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(assessment);
        when(projects.findById(2L)).thenReturn(Optional.of(Project.builder().id(2L).build()));
        AssessmentServiceImpl service = new AssessmentServiceImpl();
        ReflectionTestUtils.setField(service, "em", em);
        ReflectionTestUtils.setField(service, "assessmentRepository", assessments);
        ReflectionTestUtils.setField(service, "projectRepository", projects);
        ReflectionTestUtils.setField(service, "indicatorResultRepository", indicators);
        ReflectionTestUtils.setField(service, "reportService", report);
        ReflectionTestUtils.setField(service, "kafkaUtils", mock(KafkaUtils.class));
        service.aggregateInformation(3L,2L,1L);
        assertEquals(77.27, assessment.getOverallScore());
        assertEquals(RiskLevelEnum.LOW_RISK, assessment.getOverallRiskLevel());
        assertEquals(77.27, JSON.parseObject(assessment.getDetails(), AssessmentGeneralDetails.class)
                .getIndicatorDistributionVO().getTotalScore());
    }
}
