package com.riskwarning.enterprise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 企业服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.riskwarning.enterprise", "com.riskwarning.common"})
@EnableDiscoveryClient
@EnableFeignClients
public class EnterpriseApplication {
    public static void main(String[] args) {
        SpringApplication.run(EnterpriseApplication.class, args);
    }
}
