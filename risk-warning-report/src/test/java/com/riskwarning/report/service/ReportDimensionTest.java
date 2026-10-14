package com.riskwarning.report.service;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskwarning.common.enums.RiskDimensionEnum;
import com.riskwarning.common.enums.risk.RiskLevelEnum;
import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.po.risk.Risk;
import com.riskwarning.report.entity.vo.AssessmentGeneralDetails;
import com.riskwarning.report.entity.vo.general.AssessmentDetailVO;
import com.riskwarning.report.repository.IndicatorResultRepository;
import com.riskwarning.report.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportDimensionTest {
    private static final String[] NAMES = {"企业关联方风险", "产品法律风险", "劳动法律风险", "企业信用风险", "国际化经营风险", "供应链风险"};
    private final ObjectMapper mapper = new ObjectMapper();
    private final IndicatorResultRepository repository = mock(IndicatorResultRepository.class);
    private final ReportServiceImpl service = spy(new ReportServiceImpl());
    private final Assessment assessment = Assessment.builder().id(1L).build();

    ReportDimensionTest() {
        ReflectionTestUtils.setField(service, "indicatorResultRepository", repository);
    }

    @Test void legacyDimensionsAreNormalizedThroughoutReportJson() throws Exception {
        List<IndicatorResult> rows = new ArrayList<>();
        List<Risk> risks = new ArrayList<>();
        for (RiskDimensionEnum dimension : RiskDimensionEnum.values()) {
            rows.add(IndicatorResult.builder().dimension(dimension.getDescription())
                    .calculatedScore(8.0).maxPossibleScore(10.0).build());
            risks.add(Risk.builder().dimension(dimension.getDescription()).riskLevel(RiskLevelEnum.LOW_RISK).build());
        }
        when(repository.findByAssessmentId(1L)).thenReturn(rows);
        doReturn(risks).when(service).fetchRisksFromES(1L);
        JsonNode indicators = mapper.valueToTree(service.assembleIndicatorResult(assessment));
        JsonNode riskJson = mapper.valueToTree(service.assembleRisk(1L));
        AssessmentDetailVO general = service.assembleGeneral(assessment, risks);
        JsonNode generalJson = mapper.valueToTree(general);
        for (int i = 0; i < NAMES.length; i++) {
            assertEquals(NAMES[i], indicators.path("indicatorScores").get(i).path("dimension").asText());
            assertEquals(NAMES[i], riskJson.get(i).path("dimension").asText());
            assertEquals(NAMES[i], indicators.path("dimensionDistributions").path(NAMES[i]).path("riskDimensionEnum").asText());
            assertEquals(1, generalJson.path("dimensionRiskDistribution").path(NAMES[i]).path("riskCount").asInt());
            assertEquals(RiskDimensionEnum.values()[i].getDescription(), risks.get(i).getDimension());
        }
        assertEquals(6, generalJson.path("dimensionRiskDistribution").size());
        assessment.setDetails(JSON.toJSONString(new AssessmentGeneralDetails(general, null)));
        assertEquals(generalJson, mapper.valueToTree(service.assembleGeneral(assessment)));
    }

    @Test void canonicalNamesAndHistoricalInternationalAliasShareTheSameBuckets() {
        List<IndicatorResult> rows = new ArrayList<>();
        for (String name : NAMES) {
            rows.add(IndicatorResult.builder().dimension(name)
                    .calculatedScore(8.0).maxPossibleScore(10.0).build());
            rows.add(IndicatorResult.builder().dimension(name.substring(0, name.length() - 2))
                    .calculatedScore(8.0).maxPossibleScore(10.0).build());
        }
        rows.add(IndicatorResult.builder().dimension("企业国际合作风险")
                .calculatedScore(8.0).maxPossibleScore(10.0).build());
        when(repository.findByAssessmentId(1L)).thenReturn(rows);
        JsonNode result = mapper.valueToTree(service.assembleIndicatorResult(assessment));
        assertEquals(6, result.path("dimensionDistributions").size());
        assertEquals(3, result.path("dimensionDistributions").path("国际化经营风险").path("totalCount").asInt());
    }
}
