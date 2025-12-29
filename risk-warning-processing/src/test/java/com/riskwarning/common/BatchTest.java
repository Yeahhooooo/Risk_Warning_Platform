package com.riskwarning.common;

import com.riskwarning.common.enums.DataSourceTypeEnum;
import com.riskwarning.common.message.BehaviorProcessingTaskMessage;
import com.riskwarning.processing.ProcessingApplication;
import com.riskwarning.processing.batch.BatchJob;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest(classes = ProcessingApplication.class)
public class BatchTest {


    @Autowired
    private BatchJob batchJob;

    @Test
    public void testBatch() {
        Map<Integer, Integer> map = new HashMap<>();
        for(Map.Entry<Integer, Integer> entry : map.entrySet()){
            int key = entry.getKey();
            int value = entry.getValue();
        }

        BehaviorProcessingTaskMessage behaviorProcessingTaskMessage = new BehaviorProcessingTaskMessage();
        behaviorProcessingTaskMessage.setProjectId(1111l);
        behaviorProcessingTaskMessage.setUserId(222l);
        behaviorProcessingTaskMessage.setType(DataSourceTypeEnum.FILE_UPLOAD);
        behaviorProcessingTaskMessage.setFilePaths(new ArrayList<>());
        behaviorProcessingTaskMessage.getFilePaths().add("/Users/huayecai/Desktop/bach_01/Risk_Warning_Platform/documents/intern/behavior.txt");
        behaviorProcessingTaskMessage.getFilePaths().add("/Users/huayecai/Desktop/bach_01/Risk_Warning_Platform/documents/intern/behavior_temp.txt");
        try {
            batchJob.runBatchJob(behaviorProcessingTaskMessage.getProjectId(), behaviorProcessingTaskMessage.getFilePaths() );
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
