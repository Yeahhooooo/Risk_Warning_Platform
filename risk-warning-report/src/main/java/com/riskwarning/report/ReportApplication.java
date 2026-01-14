package com.riskwarning.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 报告服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.riskwarning.report", "com.riskwarning.common"})
@EntityScan(basePackages = "com.riskwarning.common.po")
@EnableDiscoveryClient
@EnableFeignClients
@EnableTransactionManagement
public class ReportApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReportApplication.class, args);
    }
}
