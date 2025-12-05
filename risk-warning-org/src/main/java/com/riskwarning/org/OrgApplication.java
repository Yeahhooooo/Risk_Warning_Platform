package com.riskwarning.org;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 用户认证服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.riskwarning.org", "com.riskwarning.common"})
@EntityScan(basePackages = "com.riskwarning.common.po")
@EnableJpaRepositories(basePackages = "com.riskwarning.org.repository")
@EnableDiscoveryClient
public class OrgApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrgApplication.class, args);
    }
}
