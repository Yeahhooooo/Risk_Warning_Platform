package com.riskwarning.processing.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.riskwarning.common.po.behavior.Behavior;
import com.riskwarning.common.po.indicator.Indicator;
import com.riskwarning.common.po.regulation.Regulation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BehaviorProcessingVectorTest {
    private final ElasticsearchClient es = mock(ElasticsearchClient.class);
    private final BehaviorProcessingService service = new BehaviorProcessingService();
    private final List<Float> vector = Arrays.asList(0.1f, 0.2f);

    @BeforeEach
    void inject() { ReflectionTestUtils.setField(service, "esClient", es); }

    private Behavior behavior() {
        Behavior b = new Behavior();
        b.setId("behavior-1");
        b.setProjectId(257L);
        b.setDescriptionVector(vector);
        return b;
    }

    private <T> SearchResponse<T> response(T source) {
        return SearchResponse.of(r -> r.took(0).timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h.hits(hit -> hit.index("test").id("doc-1").source(source).score(0.9))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void behaviorReadRequestsStoredVectorExplicitly() throws Exception {
        when(es.search(any(SearchRequest.class), eq(Behavior.class))).thenAnswer(call -> {
            SearchRequest request = call.getArgument(0);
            Behavior result = behavior();
            // Reproduce the live server: default _source omits stored vectors.
            if (request.source() == null || !request.source().filter().includes().contains("description_vector")) {
                result.setDescriptionVector(null);
            }
            return response(result);
        });
        List<Behavior> result = ReflectionTestUtils.invokeMethod(service, "fetchRandomBehaviors", 257L);
        assertEquals(vector, result.get(0).getDescriptionVector());
    }

    @Test
    @SuppressWarnings("unchecked")
    void candidateQueriesRequestVectorsForLocalSimilarity() throws Exception {
        when(es.search(any(SearchRequest.class), eq(Indicator.class))).thenAnswer(call -> {
            SearchRequest request = call.getArgument(0);
            assertNotNull(request.source());
            assertTrue(request.source().filter().includes().contains("name_vector"));
            return response(Indicator.builder().id("i1").nameVector(vector).build());
        });
        when(es.search(any(SearchRequest.class), eq(Regulation.class))).thenAnswer(call -> {
            SearchRequest request = call.getArgument(0);
            assertNotNull(request.source());
            assertTrue(request.source().filter().includes().contains("full_text_vector"));
            return response(Regulation.builder().id("r1").fullTextVector(vector).build());
        });
        assertEquals(1, service.fetchTopIndicators(behavior(), 6).size());
        assertEquals(1, service.fetchTopRegulations(behavior(), 10).size());
    }

    @Test
    void missingVectorFailsClearlyInsteadOfReturningEmptyResults() {
        Behavior b = behavior();
        b.setDescriptionVector(null);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.fetchTopIndicators(b, 6));
        assertTrue(error.getMessage().contains("behavior-1"));
        verifyNoInteractions(es);
    }

    @Test
    @SuppressWarnings("unchecked")
    void transportFailureIsNotConvertedToSuccessfulEmptyResult() throws Exception {
        when(es.search(any(SearchRequest.class), eq(Indicator.class))).thenThrow(new IOException("ES unavailable"));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.fetchTopIndicators(behavior(), 6));
        assertInstanceOf(IOException.class, error.getCause());
    }

    @Test
    void zeroIndicatorsCannotPassTheSaveStage() {
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(service,
                "batchSaveIndicatorResults", Collections.emptyMap(), Collections.emptyMap(), 257L, 339L));
    }
}
