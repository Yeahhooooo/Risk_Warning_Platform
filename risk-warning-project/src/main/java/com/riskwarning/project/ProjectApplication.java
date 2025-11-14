package com.riskwarning.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 项目服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.riskwarning.project", "com.riskwarning.common"})
@EnableDiscoveryClient
@EnableFeignClients
public class ProjectApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProjectApplication.class, args);
    }
}
