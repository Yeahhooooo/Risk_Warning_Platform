package com.riskwarning.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "vectorization")
@Data
public class VectorConfig {
    
    private Integer dimension = 768;
    private Integer batchSize = 32;
    private Integer maxSequenceLength = 512;
    private String mode = "api";
    private Double similarityThreshold = 0.3;
    
    private ApiConfig api = new ApiConfig();
    
    @Data
    public static class ApiConfig {
        private String localApiUrl = "http://localhost:8000/encode";
        private String apiKey;
    }
}

