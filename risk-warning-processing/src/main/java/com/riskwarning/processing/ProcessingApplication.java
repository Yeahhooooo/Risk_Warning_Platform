package com.riskwarning.processing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 行为处理服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.riskwarning.processing", "com.riskwarning.common"})
@EntityScan(basePackages = "com.riskwarning.common.po")
@EnableJpaRepositories(basePackages = "com.riskwarning.processing.repository")
@EnableDiscoveryClient
@EnableFeignClients
@EnableTransactionManagement
public class ProcessingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProcessingApplication.class, args);
    }
}
