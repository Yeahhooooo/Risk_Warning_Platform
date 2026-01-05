package com.riskwarning.processing.batch;

import com.riskwarning.common.message.IndicatorCalculationTaskMessage;
import com.riskwarning.common.utils.KafkaUtils;
import com.riskwarning.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Slf4j
public class JobListener implements JobExecutionListener {

    public static final String RESULT_FILE_PATH = "resultFilePath";

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("before job");
    }

    @Autowired
    private KafkaUtils kafkaUtils;

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("after job");
        JobParameters jobParameters = jobExecution.getJobParameters();
        String resultFilePath = jobParameters.getString(RESULT_FILE_PATH);
        log.info("resultFilePath: {}", resultFilePath);
        // todo: 作业完成后发送消息队列开始指标计算
        IndicatorCalculationTaskMessage indicatorCalculationTaskMessage = new IndicatorCalculationTaskMessage(
                StringUtils.generateMessageId(),
                LocalDateTime.now().toString(),
                StringUtils.generateTraceId(),
                null,
                Long.parseLong(jobParameters.getString("projectId")),
                null
        );
        kafkaUtils.sendMessage(indicatorCalculationTaskMessage);
    }
}
