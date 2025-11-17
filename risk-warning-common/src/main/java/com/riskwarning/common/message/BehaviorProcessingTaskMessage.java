package com.riskwarning.common.message;

import com.riskwarning.common.enums.DataSourceTypeEnum;
import com.riskwarning.common.enums.KafkaTopic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class BehaviorProcessingTaskMessage extends Message {

    private List<DataSource> dataSourceList;

    @AllArgsConstructor
    @Data
    public class DataSource {
        private DataSourceTypeEnum type;
        private String fileUrl;
    }

    public BehaviorProcessingTaskMessage() {
        this.setTopic(KafkaTopic.BEHAVIOR_PROCESSING_TASKS);
    }

    public BehaviorProcessingTaskMessage(String messageId, String timestamp, String traceId, String userId, String projectId, String enterpriseId, String assessmentId, List<DataSource> dataSourceList) {
        super(messageId, timestamp, traceId, userId, projectId, enterpriseId, assessmentId);
        this.setTopic(KafkaTopic.BEHAVIOR_PROCESSING_TASKS);
        this.dataSourceList = dataSourceList;
    }

}


