package com.riskwarning.processing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

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
        executor.setCorePoolSize(20);  // 核心线程数：增加到20
        executor.setMaxPoolSize(50);  // 最大线程数：增加到50
        executor.setQueueCapacity(100);  // 等待队列容量：增加到100
        executor.setThreadNamePrefix("Behavior-");  // 线程名称前缀
        // 队列满时由提交任务的线程直接执行，避免行为任务被拒绝后 CountDownLatch
        // 永远等不到归零，也避免后续评估长期无响应。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setKeepAliveSeconds(60);  // 空闲线程存活时间
        executor.setWaitForTasksToCompleteOnShutdown(true);  // 关闭时等待任务完成
        executor.setAwaitTerminationSeconds(60);  // 等待时间
        return executor;
    }

    /**
     * 只负责协调一次完整评估。必须与 BehaviorProcessTaskThreadPool 分离：
     * 协调任务会等待行为子任务结束，若共用线程池会产生线程饥饿。
     */
    @Bean(name = "AssessmentCoordinatorTaskThreadPool")
    public ThreadPoolTaskExecutor assessmentCoordinatorTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("Assessment-Coordinator-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        return executor;
    }
}
