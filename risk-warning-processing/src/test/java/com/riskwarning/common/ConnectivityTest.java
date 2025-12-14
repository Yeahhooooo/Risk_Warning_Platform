package com.riskwarning.common;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.riskwarning.processing.ProcessingApplication;
import com.riskwarning.common.dto.IndicatorResultDTO;
import com.riskwarning.common.enums.KafkaTopic;
import com.riskwarning.common.message.*;
import com.riskwarning.common.po.indicator.Indicator;
import com.riskwarning.common.utils.KafkaUtils;
import com.riskwarning.common.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
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
    private KafkaUtils kafkaUtils;

    @Test
    public void testDatabaseConnection() {
        try {
            // 尝试获取PostgreSQL连接以验证连接
            new ArrayList<IndicatorResultDTO>().clear();
            Connection dbConnection = dataSource.getConnection();
            System.out.println("Connected to Database: " + dbConnection.getMetaData().getURL());
        } catch (Exception e) {
            e.printStackTrace();
            assert false : "Failed to connect to the database";
        }
    }

    @Test
    public void testElasticsearchConnection() {
        try {
            // 尝试获取集群信息以验证连接
            InfoResponse infoResponse = client.info();
            System.out.println("Connected to Elasticsearch cluster: " + infoResponse.clusterName());
            List<Indicator> list = findAllIndicators();
            System.out.println("Successfully queried indicators from Elasticsearch." + list.size());
        } catch (Exception e) {
            e.printStackTrace();
            assert false : "Failed to connect to Elasticsearch";
        }
    }

    public List<Indicator> findAllIndicators() {
        try {
            SearchResponse<Indicator> response = client.search(
                    s -> s
                            .index("t_indicator")
                            .size(1200), // 设置返回的文档数量上限
                    Indicator.class);

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
            Message message = new Message();
            message.setTopic(KafkaTopic.TEST_TOPIC);
            message.setMessageId("test-message-id");
            message.setTraceId("test-trace-id");
            message.setTimestamp(String.valueOf(System.currentTimeMillis()));
            message.setUserId(111L);
            message.setProjectId(111l);
            message.setEnterpriseId(111l);
            message.setAssessmentId(1l);
            kafkaUtils.sendMessage(message);
            String result = kafkaUtils.receiveMessage();
            System.out.println("Received message from Kafka: " + result);
            assert result.contains("test-message-id");
        } catch (Exception e) {
            e.printStackTrace();
            assert false : "Failed to connect to Kafka";
        }
    }


}