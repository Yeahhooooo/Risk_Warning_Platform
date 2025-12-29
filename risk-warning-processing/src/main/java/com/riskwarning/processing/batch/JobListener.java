package com.riskwarning.processing.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JobListener implements JobExecutionListener {

    public static final String RESULT_FILE_PATH = "resultFilePath";

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("before job");
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("after job");
        // todo: 读取作业完成后的json文件批量插入es
        JobParameters jobParameters = jobExecution.getJobParameters();
        String resultFilePath = jobParameters.getString(RESULT_FILE_PATH);
        log.info("resultFilePath: {}", resultFilePath);
    }
}
