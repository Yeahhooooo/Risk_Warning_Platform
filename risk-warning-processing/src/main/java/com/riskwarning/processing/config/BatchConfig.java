package com.riskwarning.processing.config;

import com.riskwarning.common.po.behavior.Behavior;
import com.riskwarning.processing.batch.*;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.*;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDateTime;

/**
 * Spring Batch配置类
 * 启用Spring Batch功能，使用内存存储
 */
@Configuration
@EnableBatchProcessing
public class BatchConfig extends DefaultBatchConfigurer {

    @Autowired
    private JobBuilderFactory jobBuilderFactory;

    @Autowired
    private StepBuilderFactory stepBuilderFactory;

    @Autowired
    private LineRangePartitioner lineRangePartitioner;

    @Autowired
    private LineRangeItemReader lineRangeItemReader;

    @Autowired
    private LineProcessor lineProcessor;

    @Autowired
    private LineRangeItemWriter lineRangeItemWriter;

    @Autowired
    private JobListener jobListener;

    @Autowired
    @Qualifier(value = "FileProcessTaskThreadPool")
    private ThreadPoolTaskExecutor fileThreadPoolTaskExecutor;

    @Autowired
    private PlatformTransactionManager transactionManager; // 这里会注入 JpaTransactionManager

    @Override
    public PlatformTransactionManager getTransactionManager() {
        return transactionManager; // 强制让 Batch 使用 JPA 的事务管理器
    }

    @Bean
    public Job job() {
        return jobBuilderFactory.get("behaviorProcessJob" + LocalDateTime.now())
                .listener(jobListener)
                .start(masterStep())
                .build();
    }

    @Bean
    public Step masterStep() {
        return stepBuilderFactory.get("masterStep")
                .partitioner("workerStep", lineRangePartitioner)
                .step(workerStep())
                .taskExecutor(fileThreadPoolTaskExecutor)
                .gridSize(Runtime.getRuntime().availableProcessors())
                .build();
    }

    @Bean
    public Step workerStep() {
        return stepBuilderFactory.get("workerStep")
                .<String, Behavior>chunk(100)
                .reader(lineRangeItemReader)
                .processor(lineProcessor)
                .writer(lineRangeItemWriter)
                .build();
    }

}
