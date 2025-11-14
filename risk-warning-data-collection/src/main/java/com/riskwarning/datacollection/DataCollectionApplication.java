package com.riskwarning.datacollection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 数据收集服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.riskwarning.datacollection", "com.riskwarning.common"})
@EnableDiscoveryClient
@EnableFeignClients
public class DataCollectionApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataCollectionApplication.class, args);
    }
}
