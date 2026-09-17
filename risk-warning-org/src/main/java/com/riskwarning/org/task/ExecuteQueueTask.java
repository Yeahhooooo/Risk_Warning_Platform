package com.riskwarning.org.task;

import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.constants.Constants;
import com.riskwarning.common.constants.RedisKey;
import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.common.enums.DataSourceTypeEnum;
import com.riskwarning.common.message.BehaviorProcessingTaskMessage;
import com.riskwarning.common.po.file.ProjectFile;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.reliability.DurableWorkHandler;
import com.riskwarning.common.reliability.DurableWorkStore;
import com.riskwarning.common.utils.*;
import com.riskwarning.org.entity.dto.UploadConfirmDto;
import com.riskwarning.org.entity.dto.UploadFileDto;
import com.riskwarning.org.repository.AssessmentRepository;
import com.riskwarning.org.repository.FileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/** Redis-to-database bridge; the durable worker owns the business transaction. */
@Component
@Slf4j
public class ExecuteQueueTask implements DurableWorkHandler, SmartLifecycle {
    private final UploadTaskQueue queue;
    private final DurableWorkStore store;
    private final RedisUtil redis;
    private final FileRepository files;
    private final AssessmentRepository assessments;
    private final KafkaUtils kafka;
    private volatile boolean running;
    private ScheduledExecutorService executor;

    public ExecuteQueueTask(UploadTaskQueue queue, DurableWorkStore store, RedisUtil redis,
            FileRepository files, AssessmentRepository assessments, KafkaUtils kafka) {
        this.queue = queue; this.store = store; this.redis = redis;
        this.files = files; this.assessments = assessments; this.kafka = kafka;
    }

    public void handoffOne() {
        UploadConfirmDto original = queue.poll();
        if (original == null) return;
        // Keep the Redis value unchanged so recovery retains its stable identity.
        UploadConfirmDto task = JSON.parseObject(JSON.toJSONString(original), UploadConfirmDto.class);
        if (task.getTaskId() == null) {
            task.setTaskId(UUID.nameUUIDFromBytes((queue.getQueueKey() + "\n" + JSON.toJSONString(original))
                    .getBytes(StandardCharsets.UTF_8)).toString());
        }
        if (task.getFiles() == null) {
            Map<Object, Object> metadata = redis.hmget(String.format(RedisKey.REDIS_KEY_FILE_UPLOAD_INFO, task.getProjectId()));
            List<UploadFileDto> snapshot = new ArrayList<>();
            if (metadata != null) for (Object value : metadata.values()) snapshot.add((UploadFileDto) value);
            task.setFiles(snapshot);
        }
        store.enqueue(kind(), task.getTaskId(), JSON.toJSONString(task));
        queue.acknowledge(original);
        log.info("[AssessmentFlow] stage=UPLOAD_HANDOFF status=PERSISTED projectId={} taskId={}", task.getProjectId(), task.getTaskId());
    }

    public String kind() { return "UPLOAD_INBOX"; }

    public void execute(String payload) {
        UploadConfirmDto task = JSON.parseObject(payload, UploadConfirmDto.class);
        if (task.getFiles() == null || task.getFiles().isEmpty()) {
            throw new IllegalStateException("Upload metadata missing; source input retained for recovery");
        }
        ProjectFile projectFile = ProjectFile.builder().projectId(task.getProjectId())
                .userId(task.getUserId()).filePaths(new ArrayList<>()).build();
        for (UploadFileDto file : task.getFiles()) {
            String target = Constants.getPersistFileDirPath(task.getProjectId())
                    + StringUtils.generateFileName(task.getProjectId(), file.getUploadId()) + "." + file.getFileSuffix();
            FileUtils.union(file.getFilePath(), target, false);
            projectFile.getFilePaths().add(target);
        }
        files.save(projectFile);
        Assessment assessment = Assessment.builder().projectId(task.getProjectId())
                .status(AssessmentStatusEnum.TO_BE_ASSESSED).createdAt(LocalDateTime.now()).build();
        assessments.save(assessment);
        kafka.sendMessage(new BehaviorProcessingTaskMessage(task.getTaskId() + "-behavior",
                String.valueOf(System.currentTimeMillis()), task.getTaskId(), task.getUserId(),
                task.getProjectId(), assessment.getId(), DataSourceTypeEnum.FILE_UPLOAD, projectFile.getFilePaths()));
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "upload-durable-handoff"); thread.setDaemon(true); return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try { handoffOne(); }
            catch (Exception failure) { log.error("Upload handoff failed; Redis task retained for retry", failure); }
        }, 1, 1, TimeUnit.SECONDS);
    }
    public synchronized void stop() {
        running = false;
        if (executor != null) executor.shutdownNow();
    }
    public boolean isRunning() { return running; }
}
