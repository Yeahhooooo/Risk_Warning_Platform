package com.riskwarning.processing.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.riskwarning.common.po.behavior.Behavior;
import com.riskwarning.common.po.indicator.Indicator;
import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.po.indicator.IndicatorResultDetail;
import com.riskwarning.common.po.risk.RelatedIndicator;
import com.riskwarning.processing.repository.IndicatorResultRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndicatorBehaviorPersistenceTest {
    private final BehaviorProcessingService service = new BehaviorProcessingService();
    private final IndicatorResultRepository repository = mock(IndicatorResultRepository.class);

    IndicatorBehaviorPersistenceTest() {
        ReflectionTestUtils.setField(service, "indicatorResultRepository", repository);
    }

    private RelatedIndicator calculate(String behaviorId, String description) {
        return service.computeMappingFromCandidates(Behavior.builder().id(behaviorId).projectId(2L)
                        .description(description).status("已完成").build(),
                Collections.singletonList(new BehaviorProcessingService.Scored<>(
                        Indicator.builder().id("i1").maxScore(100.0).build(), 1.0)), Collections.emptyList())
                .getRelatedIndicators().get("i1");
    }

    @Test void fallbackCalculationsKeepBehaviorIdentityAndDescription() {
        RelatedIndicator calculated = calculate("b1", "已签订合同");
        JSONObject evidence = JSON.parseObject(JSON.toJSONString(calculated.getRelatedBehaviors().get(0)));
        assertEquals("b1", evidence.getString("behaviorId"));
        assertEquals("已签订合同", evidence.getString("description"));
    }

    @Test void newIndicatorPersistsDistinctBehaviorIdsAlongsideEvidence() {
        IndicatorResult saved = save(Arrays.asList(calculate("b1", "行为一"), calculate("b2", "行为二"),
                calculate("b1", "行为一"), calculate(null, "无ID的旧行为")));
        assertEquals(Arrays.asList("b1", "b2"), asJson(saved).getList("matchedBehaviorIds", String.class));
        assertEquals(4, saved.getCalculationDetails().getRelatedIndicators().size());
        assertEquals(100.0, saved.getCalculatedScore());
    }

    @Test void updatingIndicatorMergesPersistedIdsAndOldEvidenceWithoutDuplicates() {
        IndicatorResult existing = JSON.parseObject("{\"matchedBehaviorIds\":[\"older-id\",\"b1\"]}", IndicatorResult.class);
        existing.setCalculatedScore(100.0);
        existing.setCalculationDetails(IndicatorResultDetail.builder().relatedIndicators(
                new ArrayList<>(Collections.singletonList(calculate("b1", "行为一")))).build());
        when(repository.findByAssessmentIdAndIndicatorEsId(1L, "i1")).thenReturn(Optional.of(existing));
        IndicatorResult saved = save(Arrays.asList(calculate("b1", "行为一"), calculate("b2", "行为二")));
        assertEquals(Arrays.asList("older-id", "b1", "b2"), asJson(saved).getList("matchedBehaviorIds", String.class));
        assertEquals(100.0, saved.getCalculatedScore());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private IndicatorResult save(List<RelatedIndicator> calculations) {
        ReflectionTestUtils.invokeMethod(service, "batchSaveIndicatorResults",
                Collections.singletonMap("i1", calculations), Collections.emptyMap(), 2L, 1L);
        ArgumentCaptor<List<IndicatorResult>> saved = ArgumentCaptor.forClass((Class) List.class);
        verify(repository).saveAllAndFlush(saved.capture());
        return saved.getValue().get(0);
    }

    private JSONObject asJson(IndicatorResult result) {
        return JSON.parseObject(JSON.toJSONString(result));
    }
}
