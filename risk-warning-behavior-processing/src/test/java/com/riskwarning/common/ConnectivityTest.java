package com.riskwarning.common;


import cn.hutool.core.bean.BeanUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import com.riskwarning.behavior.BehaviorProcessingApplication;
import com.riskwarning.common.config.ElasticSearchConfig;
import com.riskwarning.common.dto.IndicatorResultDTO;
import com.riskwarning.common.enums.DataSourceTypeEnum;
import com.riskwarning.common.enums.KafkaTopic;
import com.riskwarning.common.message.*;
import com.riskwarning.common.utils.KafkaUtils;
import com.riskwarning.common.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Collections;


@SpringBootTest(classes = BehaviorProcessingApplication.class)
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
        } catch (Exception e) {
            e.printStackTrace();
            assert false : "Failed to connect to Elasticsearch";
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
            message.setUserId("test-user-id");
            message.setProjectId("test-project-id");
            message.setEnterpriseId("test-enterprise-id");
            message.setAssessmentId("test-assessment-id");
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