package com.riskwarning.common.message;

import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.enums.KafkaTopic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class Message {

    private KafkaTopic topic;

    private String messageId; // UUID

    private String timestamp;

    private String traceId;

    private Long userId;

    private Long projectId;

    private Long assessmentId;

    public Message(String messageId, String timestamp, String traceId, Long userId, Long projectId, Long assessmentId) {
        this.messageId = messageId;
        this.timestamp = timestamp;
        this.traceId = traceId;
        this.userId = userId;
        this.projectId = projectId;
        this.assessmentId = assessmentId;
    }

    public String toJson() {
        return JSON.toJSONString(this);
    }

}
