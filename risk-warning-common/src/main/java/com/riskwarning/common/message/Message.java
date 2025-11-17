package com.riskwarning.common.message;

import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.enums.KafkaTopic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class Message {

    private KafkaTopic topic;

    private String messageId; // UUID

    private String timestamp;

    private String traceId;

    private String userId;

    private String projectId;

    private String enterpriseId;

    private String assessmentId;

    public Message(String messageId, String timestamp, String traceId, String userId, String projectId, String enterpriseId, String assessmentId) {
        this.messageId = messageId;
        this.timestamp = timestamp;
        this.traceId = traceId;
        this.userId = userId;
        this.projectId = projectId;
        this.enterpriseId = enterpriseId;
        this.assessmentId = assessmentId;
    }

    public String toJson() {
        return JSON.toJSONString(this);
    }

}
