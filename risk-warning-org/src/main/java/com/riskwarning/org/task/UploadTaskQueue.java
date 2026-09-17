package com.riskwarning.org.task;

import com.riskwarning.common.exception.BusinessException;
import com.riskwarning.common.utils.RedisUtil;
import com.riskwarning.org.entity.dto.UploadConfirmDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Both ends must use the same queue; developers sharing Redis need separate keys. */
@Component
@Slf4j
public class UploadTaskQueue {
    private final RedisUtil redis;
    private final String queueKey;

    public UploadTaskQueue(RedisUtil redis,
            @Value("${org.upload.queue-key:file:confirmed:queue}") String queueKey) {
        if (queueKey == null || queueKey.trim().isEmpty()) {
            throw new IllegalArgumentException("org.upload.queue-key must not be blank");
        }
        this.redis = redis;
        this.queueKey = queueKey;
        log.info("[AssessmentFlow] stage=UPLOAD_QUEUE status=CONFIGURED queueKey={}", queueKey);
    }

    public void enqueue(UploadConfirmDto task) {
        if (!redis.lSet(queueKey, task)) {
            log.error("[AssessmentFlow] stage=UPLOAD_QUEUE_ENQUEUE status=FAILED projectId={} queueKey={}", task.getProjectId(), queueKey);
            throw new BusinessException("评估任务入队失败，请检查 Redis 连接后重试");
        }
        log.info("[AssessmentFlow] stage=UPLOAD_QUEUE_ENQUEUE status=QUEUED projectId={} queueKey={}", task.getProjectId(), queueKey);
    }

    public UploadConfirmDto poll() {
        return (UploadConfirmDto) redis.claimListItem(queueKey, queueKey + ":processing");
    }

    public void acknowledge(Object task) {
        redis.acknowledgeListItem(queueKey + ":processing", task);
    }

    public String getQueueKey() {
        return queueKey;
    }
}
