package com.riskwarning.common;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.riskwarning.common.config.TestConsumer;
import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.common.enums.indicator.IndicatorRiskStatus;
import com.riskwarning.common.enums.risk.RiskLevelEnum;
import com.riskwarning.common.po.behavior.Behavior;
import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.po.regulation.Regulation;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.processing.ProcessingApplication;
import com.riskwarning.common.dto.IndicatorResultDTO;
import com.riskwarning.common.enums.KafkaTopic;
import com.riskwarning.common.message.*;
import com.riskwarning.common.po.indicator.Indicator;
import com.riskwarning.common.utils.RedisUtil;
import com.riskwarning.processing.repository.AssessmentRepository;
import com.riskwarning.processing.repository.IndicatorResultRepository;
import com.riskwarning.processing.service.BehaviorProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


@SpringBootTest(classes = ProcessingApplication.class)
public class ConnectivityTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private RedisUtil redisUtil;


    @Autowired
    private KafkaTemplate<String, Message> kafkaTemplate;

    @Autowired
    private TestConsumer testConsumer;

    @Autowired
    private IndicatorResultRepository indicatorResultRepository;

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private BehaviorProcessingService behaviorProcessingService;

    @Test
    @Transactional
    public void testDatabaseConnection() {
        try {
            // 尝试获取PostgreSQL连接以验证连接

            Assessment assessment = assessmentRepository.findById(88L).get();

            IndicatorResult ir = IndicatorResult.builder()
                    .projectId(7L)
                    .assessmentId(88L)
                    .indicatorEsId("indicatorEsId")
                    .indicatorName("test")
                    .indicatorLevel(0)
                    .dimension("test")
                    .type("test")
                    .calculatedScore(0.0)
                    .maxPossibleScore(1.0)
                    .usedCalculationRuleType("auto")
                    .calculationDetails(null)
                    .riskTriggered(false)
                    .riskStatus(IndicatorRiskStatus.fromCode("NOT_EVALUATED"))
                    .calculatedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();
            indicatorResultRepository.saveAndFlush(ir);
            System.out
                    .println("Connected to the database and saved IndicatorResult with ID: ");
        } catch (Exception e) {
            e.printStackTrace();
            assert false : "Failed to connect to the database";
        }
    }

    @Test
    @Transactional
    public void testAssessmentQuery() {
        try {

            Assessment assessment1 = assessmentRepository.findById(88L).get();

            Assessment assessment = Assessment.builder()
                    .projectId(1000L)
                    .assessmentDate(LocalDateTime.now())
                    .overallScore(0.0)
                    .overallRiskLevel(RiskLevelEnum.LOW_RISK)
                    .details("")
                    .recommendations("")
                    .status(AssessmentStatusEnum.ASSESSING)
                    .createdAt(LocalDateTime.now())
                    .build();
            assessmentRepository.saveAndFlush(assessment);

            Assessment assessment2 = assessmentRepository.findByProjectId(1000L);

            assert assessment2.getOverallScore() == 0.0;
        } catch (Exception e) {
            e.printStackTrace();
            assert false : "Failed to query assessments from the database";
        }
    }

    @Test
    public void testElasticsearchConnection() {
        try {
            // 尝试获取集群信息以验证连接
            InfoResponse infoResponse = client.info();
            System.out.println("Connected to Elasticsearch cluster: " + infoResponse.clusterName());
            List<Indicator> indicatorList = findAll("t_indicator", Indicator.class);
            List<Behavior> behaviorList = findAll("t_behavior", Behavior.class);
            List<Regulation> regulationList = findAll("t_regulation", Regulation.class);
            System.out.println("Successfully queried indicators from Elasticsearch." + indicatorList.size());
            System.out.println("Successfully queried behaviors from Elasticsearch." + behaviorList.size());
            System.out.println("Successfully queried regulation from Elasticsearch." + regulationList.size());
            System.out.println("Successfully queried regulation from Elasticsearch.");
        } catch (Exception e) {
            e.printStackTrace();
            assert false : "Failed to connect to Elasticsearch";
        }
    }

    public <T> List<T> findAll(String indexName, Class<T> clazz) {
        try {
            SearchResponse<T> response = client.search(
                    s -> s
                            .index(indexName)
                            .size(10), // 设置返回的文档数量上限
                    clazz);

            return response.hits().hits().stream()
                    .map(hit -> hit.source()).collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("查询所有指标时发生错误: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("查询失败");
        }
    }

    @Test
    public void testRedisConnection() {
        try {
            redisUtil.set("connection_test_key", "connection_test_value");
            String value = (String) redisUtil.get("connection_test_key");
            assert "connection_test_value".equals(value) : "Redis value mismatch";
            System.out.println("Connected to Redis and verified key-value pair.");
        } catch (Exception e) {
            e.printStackTrace();
            assert false : "Failed to connect to Redis";
        }
    }


    @Test
    public void testKafkaConnection() {
        try {
            // basic Message
            BehaviorProcessingTaskMessage message = new BehaviorProcessingTaskMessage();
            message.setTopic(KafkaTopic.TEST_TOPIC);
            message.setMessageId("test-message-id");
            message.setTraceId("test-trace-id");
            message.setTimestamp(String.valueOf(System.currentTimeMillis()));
            message.setUserId(111L);
            message.setProjectId(111l);
            message.setAssessmentId(1l);
            message.setFilePaths(new ArrayList<>());
            message.getFilePaths().add("test");
            message.getFilePaths().add("test");

            String topic = message.getTopic().getTopicName();
            kafkaTemplate.send(topic, message);
            Message result = testConsumer.awaitMessage();
            System.out.println("Received message from Kafka: " + result);
            assert result != null;
        } catch (Exception e) {
            e.printStackTrace();
            assert false : "Failed to connect to Kafka";
        }
    }



}