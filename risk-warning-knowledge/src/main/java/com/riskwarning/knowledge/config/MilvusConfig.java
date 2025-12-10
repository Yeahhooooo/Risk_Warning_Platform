package com.riskwarning.knowledge.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "milvus")
@Data
public class MilvusConfig {
    
    private String host = "localhost";
    private Integer port = 19530;
    private String database = "default";
    private Long timeout = 30000L;
    
    @Bean
    public MilvusServiceClient milvusClient() {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withDatabaseName(database)
                .build();
        
        return new MilvusServiceClient(connectParam);
    }
}

