package com.riskwarning.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.po.indicator.IndicatorResultDetail;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.po.risk.RelatedBehavior;
import com.riskwarning.common.po.risk.RelatedIndicator;
import com.riskwarning.report.repository.IndicatorResultRepository;
import com.riskwarning.report.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportRelatedBehaviorsTest {
    private final IndicatorResultRepository repository = mock(IndicatorResultRepository.class);
    private final ReportServiceImpl service = new ReportServiceImpl();
    private final ObjectMapper mapper = new ObjectMapper();

    ReportRelatedBehaviorsTest() {
        ReflectionTestUtils.setField(service, "indicatorResultRepository", repository);
    }

    @Test void returnsOnlyDistinctDescriptionsForEachIndicatorIncludingDimensionBreakdown() {
        RelatedBehavior first = RelatedBehavior.builder().projectId(99L).description("已签订劳动合同").build();
        RelatedBehavior second = RelatedBehavior.builder().projectId(99L).description("定期开展安全培训").build();
        IndicatorResult labor = row("labor", IndicatorResultDetail.builder().relatedIndicators(Arrays.asList(
                RelatedIndicator.builder().relatedBehaviors(Arrays.asList(first, second, first, null,
                        RelatedBehavior.builder().description(" ").build())).build(), null,
                RelatedIndicator.builder().build())).build());
        IndicatorResult credit = row("credit", IndicatorResultDetail.builder().relatedIndicators(Collections.singletonList(
                RelatedIndicator.builder().relatedBehaviors(Collections.singletonList(
                        RelatedBehavior.builder().description("按期偿还借款").build())).build())).build());
        when(repository.findByAssessmentId(1L)).thenReturn(Arrays.asList(labor, credit));
        JsonNode result = mapper.valueToTree(service.assembleIndicatorResult(Assessment.builder().id(1L).build()));
        JsonNode descriptions = result.path("indicatorScores").get(0).path("relatedBehaviors");
        assertEquals(mapper.valueToTree(Arrays.asList("已签订劳动合同", "定期开展安全培训")), descriptions);
        assertEquals(mapper.valueToTree(Collections.singletonList("按期偿还借款")),
                result.path("indicatorScores").get(1).path("relatedBehaviors"));
        assertEquals(descriptions, result.path("dimensionDistributions").path("劳动法律风险")
                .path("indicatorScores").get(0).path("relatedBehaviors"));
    }

    @Test void legacyRowsWithoutEvidenceReturnEmptyArrays() {
        when(repository.findByAssessmentId(1L)).thenReturn(Arrays.asList(row("a", null),
                row("b", IndicatorResultDetail.builder().build())));
        JsonNode result = mapper.valueToTree(service.assembleIndicatorResult(Assessment.builder().id(1L).build()));
        for (JsonNode indicator : result.path("indicatorScores")) {
            assertEquals(mapper.createArrayNode(), indicator.path("relatedBehaviors"));
        }
    }

    private IndicatorResult row(String id, IndicatorResultDetail detail) {
        return IndicatorResult.builder().indicatorEsId(id).dimension("劳务合规风险")
                .calculatedScore(8.0).maxPossibleScore(10.0).calculationDetails(detail).build();
    }
}
