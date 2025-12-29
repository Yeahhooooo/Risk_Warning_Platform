package com.riskwarning.processing.batch;

import com.riskwarning.common.constants.Constants;
import com.riskwarning.common.message.BehaviorProcessingTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 批处理工具类
 * 提供Spring Batch相关的统一接口和文件处理功能
 */
@Slf4j
@Component
public class BatchJob {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job job;


    /**
     * 执行批处理任务 - 同步方式
     *
     * @return JobExecution 作业执行结果
     * @throws JobExecutionAlreadyRunningException 作业已在运行异常
     * @throws JobRestartException 作业重启异常
     * @throws JobInstanceAlreadyCompleteException 作业实例已完成异常
     * @throws JobParametersInvalidException 作业参数无效异常
     */
    public JobExecution runBatchJob(Long projectId, List<String> filePaths)
            throws JobExecutionAlreadyRunningException, JobRestartException,
                   JobInstanceAlreadyCompleteException, JobParametersInvalidException {

        log.info("开始执行批处理作业: {}", projectId);
        JobParameters params = new JobParametersBuilder()
                .addString("projectId", String.valueOf(projectId))
                .addString("filePaths", String.join(",", filePaths))
                .addString("time", LocalDateTime.now().toString()) // 保证 job 唯一
                .addString(JobListener.RESULT_DIR_PATH, Constants.getProcessingFileDirPath(projectId))
                .toJobParameters();
        JobExecution jobExecution = jobLauncher.run(job, params);
        log.info("批处理作业执行完成: {}, 状态: {}", projectId, jobExecution.getStatus());
        return jobExecution;
    }

//    /**
//     * 执行批处理任务 - 异步方式
//     *
//     * @return CompletableFuture<JobExecution> 异步作业执行结果
//     */
//    public CompletableFuture<JobExecution> runBatchJobAsync(BehaviorProcessingTaskMessage message) {
//        return CompletableFuture.supplyAsync(() -> {
//            log.info("开始异步执行批处理作业: {}", message.getProjectId());
//            try {
//                return runBatchJob(message);
//            } catch (Exception e) {
//                log.error("异步执行批处理作业失败: {}", e.getMessage());
//                throw new RuntimeException("异步批处理作业执行失败", e);
//            }
//        });
//    }



    /**
     * 检查作业执行状态
     *
     * @param jobExecution 作业执行对象
     * @return 是否成功完成
     */
    public boolean isJobExecutionSuccessful(JobExecution jobExecution) {
        return jobExecution != null &&
               jobExecution.getStatus() == BatchStatus.COMPLETED &&
               jobExecution.getExitStatus().getExitCode().equals(ExitStatus.COMPLETED.getExitCode());
    }

    /**
     * 获取作业执行摘要信息
     *
     * @param jobExecution 作业执行对象
     * @return 摘要信息字符串
     */
    public String getJobExecutionSummary(JobExecution jobExecution) {
        if (jobExecution == null) {
            return "作业执行对象为空";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("作业名称: ").append(jobExecution.getJobInstance().getJobName()).append("\n");
        summary.append("执行ID: ").append(jobExecution.getId()).append("\n");
        summary.append("状态: ").append(jobExecution.getStatus()).append("\n");
        summary.append("开始时间: ").append(jobExecution.getStartTime()).append("\n");
        summary.append("结束时间: ").append(jobExecution.getEndTime()).append("\n");
        summary.append("退出状态: ").append(jobExecution.getExitStatus().getExitCode()).append("\n");

        String exitDescription = jobExecution.getExitStatus().getExitDescription();
        if (exitDescription != null && !exitDescription.isEmpty()) {
            summary.append("退出描述: ").append(exitDescription).append("\n");
        }

        return summary.toString();
    }

}
