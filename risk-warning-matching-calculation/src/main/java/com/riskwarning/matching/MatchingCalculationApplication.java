package com.riskwarning.matching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 智能匹配与指标计算服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.riskwarning.matching", "com.riskwarning.common"})
@EnableDiscoveryClient
@EnableFeignClients
public class MatchingCalculationApplication {
    public static void main(String[] args) {
        SpringApplication.run(MatchingCalculationApplication.class, args);
    }
}
