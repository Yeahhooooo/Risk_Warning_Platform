package com.riskwarning.common.message;

import com.riskwarning.common.enums.DataSourceTypeEnum;
import com.riskwarning.common.enums.KafkaTopic;
import lombok.*;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class BehaviorProcessingTaskMessage extends Message {

    private DataSourceTypeEnum type;
    private List<String> filePaths;


    public BehaviorProcessingTaskMessage() {
        this.setTopic(KafkaTopic.BEHAVIOR_PROCESSING_TASKS);
    }

    public BehaviorProcessingTaskMessage(String messageId, String timestamp, String traceId, Long userId, Long projectId, Long assessmentId, DataSourceTypeEnum type, List<String> filePaths) {
        super(messageId, timestamp, traceId, userId, projectId, assessmentId);
        this.setTopic(KafkaTopic.BEHAVIOR_PROCESSING_TASKS);
        this.type = type;
        if(this.type == null) {
            throw new IllegalArgumentException("Invalid data source type: " + type);
        }
        this.filePaths = filePaths;
    }

}


