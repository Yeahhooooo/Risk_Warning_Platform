package com.riskwarning.knowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 知识库服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.riskwarning.knowledge", "com.riskwarning.common"})
@EnableDiscoveryClient
@EnableFeignClients
public class KnowledgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeApplication.class, args);
    }
}
