package com.riskwarning.processing.task;


import com.riskwarning.common.constants.Constants;
import com.riskwarning.common.message.BehaviorProcessingTaskMessage;
import com.riskwarning.common.message.Message;
import com.riskwarning.common.utils.FileUtils;
import com.riskwarning.processing.batch.BatchJob;
import com.riskwarning.processing.service.DocumentProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

@Component
@Slf4j
public class MessageTask {

    @Autowired
    private BatchJob batchJob;

    @Autowired
    private DocumentProcessingService documentProcessingService;

    @Autowired
    @Qualifier(value = "TaskThreadPool")
    private ThreadPoolTaskExecutor threadPoolExecutor;


    @KafkaListener(topics = "behavior_processing_tasks", groupId = "test-consumer")
    public void onMessage(BehaviorProcessingTaskMessage message) {
        log.info("Received message: {}", message);
        try {
            switch (message.getType()){
                case FILE_UPLOAD:
                    log.info("File upload received");
                    threadPoolExecutor.execute(() -> {
                        List<String> internalFiles = documentProcessingService.processDocument(
                                message.getProjectId(),
                                new ArrayList<>(),
                                message.getFilePaths()
                        );
                        try {
                            log.info("start batch job for projectId: {}", message.getProjectId());
                            batchJob.runBatchJob(
                                    message.getProjectId(),
                                    internalFiles
                            );
                        } catch (Exception e) {
                            log.error("Error processing file upload task", e);
                        } finally {
                            log.info("Finished processing file upload for projectId: {}", message.getProjectId());
                            // todo: 删除中间文件
                            String internalFilePath = Constants.getInternalDirPath(message.getProjectId());
                            FileUtils.delDirectory(internalFilePath);
                        }
                    });
                    break;
                case KNOWLEDGE_QUERY:
                    // todo: 处理知识查询任务
                    break;
                case QUESTIONNAIRE:
                    // todo: 处理问卷任务
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public static void main(String[] args) {
        TreeMap<Integer, Integer> rec = new TreeMap<>();
        System.out.println(1 ^ 2);
    }
}
