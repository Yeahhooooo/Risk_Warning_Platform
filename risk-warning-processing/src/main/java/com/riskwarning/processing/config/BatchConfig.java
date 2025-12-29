package com.riskwarning.processing.config;

import com.riskwarning.processing.batch.*;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.SimpleJobLauncher;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    private LineProcessor lineProcessor;

    @Autowired
    private LineRangeItemWriter lineRangeItemWriter;

    @Autowired
    private JobListener jobListener;

    @Autowired
    @Qualifier(value = "TaskThreadPool")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

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
                .partitioner("workerStep", lineRangePartitioner(null))
                .step(workerStep())
                .taskExecutor(threadPoolTaskExecutor)
                .gridSize(Runtime.getRuntime().availableProcessors())
                .build();
    }

    @Bean
    public Step workerStep() {
        return stepBuilderFactory.get("workerStep")
                .<String, String>chunk(50)
                .reader(lineRangeItemReader(null, 0, 0))
                .processor(lineProcessor)
                .writer(lineRangeItemWriter)
                .build();
    }

    @Bean
    @StepScope
    public LineRangePartitioner lineRangePartitioner(@Value("#{jobParameters['filePaths']}") String filePaths) {
        // spring初始化时filePaths可能为null，需处理
        List<String> paths = filePaths == null ? new ArrayList<>() : Arrays.asList(filePaths.split(","));
        return new LineRangePartitioner(paths);
    }

    @Bean
    @StepScope
    public LineRangeItemReader lineRangeItemReader(
            @Value("#{stepExecutionContext['filePath']}") String filePath,
            @Value("#{stepExecutionContext['startLine']}") int startLine,
            @Value("#{stepExecutionContext['endLine']}") int endLine
    ) {
        return new LineRangeItemReader(filePath, startLine, endLine);
    }
}
