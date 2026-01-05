package com.riskwarning.processing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TaskThreadPool {

    @Bean(name = "FileProcessTaskThreadPool")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);  // 核心线程数
        executor.setMaxPoolSize(10);  // 最大线程数
        executor.setQueueCapacity(20);  // 等待队列容量
        executor.setThreadNamePrefix("File-");  // 线程名称前缀
        return executor;
    }

    @Bean(name = "BehaviorProcessTaskThreadPool")
    public ThreadPoolTaskExecutor behaviorProcessTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);  // 核心线程数
        executor.setMaxPoolSize(10);  // 最大线程数
        executor.setQueueCapacity(20);  // 等待队列容量
        executor.setThreadNamePrefix("Behavior-");  // 线程名称前缀
        return executor;
    }
}
