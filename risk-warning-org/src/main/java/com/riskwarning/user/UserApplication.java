package com.riskwarning.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 用户认证服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.riskwarning.user", "com.riskwarning.common"})
@EntityScan(basePackages = "com.riskwarning.common.po")
@EnableJpaRepositories(basePackages = "com.riskwarning.user.repository")
@EnableDiscoveryClient
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
