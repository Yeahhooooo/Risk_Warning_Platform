package com.riskwarning.processing.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.riskwarning.common.config.ElasticSearchConfig;
import com.riskwarning.common.po.behavior.Behavior;
import com.riskwarning.common.po.indicator.Indicator;
import com.riskwarning.common.po.regulation.Regulation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in, read-only: no application context, database writes or Kafka publishing. */
@EnabledIfSystemProperty(named = "assessment.live.es", matches = "true")
class BehaviorProcessingLiveReadTest {
    @Test
    @SuppressWarnings("unchecked")
    void storedProjectVectorsCanBeReadAndMapped() throws Exception {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.apache.http"))
                .setLevel(ch.qos.logback.classic.Level.WARN);
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.elasticsearch.client"))
                .setLevel(ch.qos.logback.classic.Level.WARN);
        String nacosBase = System.getProperty("assessment.live.nacosBase");
        assertNotNull(nacosBase, "Supply assessment.live.nacosBase for the read-only check");
        URLConnection connection = new URL(nacosBase + "/v1/cs/configs?dataId=common-dev.yaml&group=DEFAULT_GROUP").openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        Map<String, Object> settings;
        try (InputStream input = connection.getInputStream()) {
            Map<String, Object> root = new Yaml(new SafeConstructor()).load(input);
            settings = (Map<String, Object>) ((Map<String, Object>) root.get("spring")).get("elasticsearch");
        }
        ElasticSearchConfig config = new ElasticSearchConfig();
        // Avoid ReflectionTestUtils here: its DEBUG output would print credentials.
        for (String[] pair : new String[][]{{"esUris", "uris"}, {"username", "username"}, {"password", "password"}}) {
            java.lang.reflect.Field field = ElasticSearchConfig.class.getDeclaredField(pair[0]);
            field.setAccessible(true);
            field.set(config, String.valueOf(settings.getOrDefault(pair[1], "")));
        }
        ElasticsearchClient es = config.elasticsearchClient();
        try {
            BehaviorProcessingService service = new BehaviorProcessingService();
            ReflectionTestUtils.setField(service, "esClient", es);
            Long projectId = Long.valueOf(System.getProperty("assessment.live.projectId", "257"));
            List<Behavior> behaviors = ReflectionTestUtils.invokeMethod(service, "fetchRandomBehaviors", projectId);
            assertFalse(behaviors.isEmpty());
            for (Behavior behavior : behaviors) {
                assertNotNull(behavior.getDescriptionVector());
                assertEquals(768, behavior.getDescriptionVector().size());
                List<BehaviorProcessingService.Scored<Indicator>> indicators = service.fetchTopIndicators(behavior, 6);
                List<BehaviorProcessingService.Scored<Regulation>> regulations = service.fetchTopRegulations(behavior, 10);
                assertFalse(indicators.isEmpty());
                assertFalse(regulations.isEmpty());
                assertTrue(indicators.stream().allMatch(i -> i.getItem().getNameVector() != null));
                assertTrue(regulations.stream().allMatch(r -> r.getItem().getFullTextVector() != null));
                assertFalse(service.computeMappingFromCandidates(behavior, indicators, regulations).getRelatedIndicators().isEmpty());
            }
        } finally {
            es._transport().close();
        }
    }
}
