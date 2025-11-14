package com.riskwarning.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 风险评估服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.riskwarning.risk", "com.riskwarning.common"})
@EnableDiscoveryClient
@EnableFeignClients
public class RiskAssessmentApplication {
    public static void main(String[] args) {
        SpringApplication.run(RiskAssessmentApplication.class, args);
    }
}
