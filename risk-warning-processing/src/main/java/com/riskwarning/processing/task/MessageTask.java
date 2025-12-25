package com.riskwarning.processing.task;


import com.riskwarning.common.message.BehaviorProcessingTaskMessage;
import com.riskwarning.common.message.Message;
import com.riskwarning.processing.batch.BatchJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MessageTask {

    @Autowired
    private BatchJob batchJob;

    public void onMessage(BehaviorProcessingTaskMessage message) {
        log.info("Received message: {}", message);
        try {
            switch (message.getType()){
                case FILE_UPLOAD:

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
}
