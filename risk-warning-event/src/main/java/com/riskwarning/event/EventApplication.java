package com.riskwarning.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 事件服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.riskwarning.event", "com.riskwarning.common"})
@EnableDiscoveryClient
public class EventApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventApplication.class, args);
    }
}
