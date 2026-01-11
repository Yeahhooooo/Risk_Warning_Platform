package com.riskwarning.common;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.riskwarning.common.config.ElasticSearchConfig;
import com.riskwarning.common.po.behavior.Behavior;
import com.riskwarning.common.po.indicator.Indicator;
import com.riskwarning.processing.ProcessingApplication;
import com.riskwarning.processing.service.BehaviorProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SpringBootTest(classes = ProcessingApplication.class)
@Slf4j
public class ESSearchTest {

    @Autowired
    private ElasticsearchClient esClient;

    @Test
    public void fetchTopIndicators() {

        Behavior behavior = fetchBehaviorById("3352e650-41cd-441c-a66a-ef98f575978a");

        try {
            String text = (behavior.getDescription() == null ? "" : behavior.getDescription())
                    + " " + (behavior.getTags() == null ? "" : String.join(" ", behavior.getTags()));

            log.info("[Fetching Indicator Candidates] behaviorId={}, text={}", behavior.getId(), text);

            SearchResponse<Indicator> resp = esClient.search(s -> s
                            .index(ElasticSearchConfig.INDICATOR_INDEX)
                            .size(5)
                            .knn(k -> k
                                    .field("name_vector")
                                    .queryVector(
                                            behavior.getDescriptionVector()
                                    )
                                    .k(5)
                                    .numCandidates(100)
                            ),
                    Indicator.class
            );

            List<BehaviorProcessingService.Scored<Indicator>> out = new ArrayList<>();
            if (resp != null && resp.hits() != null) {
                for (Hit<Indicator> h : resp.hits().hits()) {
                    Indicator ind = h.source();
                    double score = h.score() == null ? 0.0 : h.score();
                    out.add(new BehaviorProcessingService.Scored<>(ind, score));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        log.info("Fetching top indicators from Elasticsearch");
    }

    private Behavior fetchBehaviorById(String behaviorId) {
        try{
            SearchResponse<Behavior> response = esClient.search(s -> s
                            .index(ElasticSearchConfig.BEHAVIOR_INDEX)
                            .query(q -> q
                                    .term(t -> t
                                            .field("id")
                                            .value(v -> v.stringValue(behaviorId))
                                    )
                            )
                    , Behavior.class);

            if (response.hits().hits().isEmpty()) {
                throw new Exception("Behavior not found: " + behaviorId);
            }
            return response.hits().hits().get(0).source();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Behavior by ID: " + e.getMessage(), e);
        }
    }
}
