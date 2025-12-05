package com.riskwarning.processing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 行为处理服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.riskwarning.processing", "com.riskwarning.common"})
@EnableDiscoveryClient
@EnableFeignClients
public class ProcessingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProcessingApplication.class, args);
    }
}
