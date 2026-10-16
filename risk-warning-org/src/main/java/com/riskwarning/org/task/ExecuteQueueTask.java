package com.riskwarning.org.task;

import com.riskwarning.org.service.ProjectService;
import com.riskwarning.common.message.AssessmentCompletedEventMessage;
import org.springframework.core.NestedExceptionUtils;

import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.constants.Constants;
import com.riskwarning.common.constants.RedisKey;
import com.riskwarning.common.enums.DataSourceTypeEnum;
import com.riskwarning.common.message.BehaviorProcessingTaskMessage;
import com.riskwarning.common.po.file.ProjectFile;
import com.riskwarning.common.reliability.DurableWorkHandler;
import com.riskwarning.common.reliability.DurableWorkStore;
import com.riskwarning.common.utils.*;
import com.riskwarning.org.entity.dto.UploadConfirmDto;
import com.riskwarning.org.entity.dto.UploadFileDto;
import com.riskwarning.org.repository.FileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Redis-to-database bridge; the durable worker owns the business transaction. */
@Component
@Slf4j
public class ExecuteQueueTask implements DurableWorkHandler, SmartLifecycle {
    @org.springframework.beans.factory.annotation.Autowired
    private ProjectService projectService;
    @javax.persistence.PersistenceContext
    private javax.persistence.EntityManager em;
    private final UploadTaskQueue queue;
    private final DurableWorkStore store;
    private final RedisUtil redis;
    private final FileRepository files;
    private final KafkaUtils kafka;
    private volatile boolean running;
    private ScheduledExecutorService executor;

    public ExecuteQueueTask(UploadTaskQueue queue, DurableWorkStore store, RedisUtil redis,
            FileRepository files, KafkaUtils kafka) {
        this.queue = queue; this.store = store; this.redis = redis;
        this.files = files; this.kafka = kafka;
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
        task.setAssessmentId(projectService.prepareUploadAssessment(task.getProjectId(), task.getTaskId()));
        store.enqueue(workKind(), task.getTaskId(), JSON.toJSONString(task));
        queue.acknowledge(original);
        log.info("[AssessmentFlow] stage=UPLOAD_HANDOFF status=PERSISTED projectId={} taskId={} kind={}",
                task.getProjectId(), task.getTaskId(), workKind());
    }

    /** 工作项写入数据库时使用的 kind：始终是 UPLOAD_INBOX，由当前启用的处理器认领。 */
    private String workKind() {
        return "UPLOAD_INBOX";
    }

    /** Claim the same durable work kind that handoff persists. */
    public String kind() {
        return workKind();
    }

    public int maxAttempts() { return 1; }

    @org.springframework.context.annotation.Bean
    public DurableWorkHandler assessmentWatchdog() {
        return new DurableWorkHandler() {
            public String kind() { return "ASSESSMENT_WATCHDOG"; }
            public int maxAttempts() { return 0; }
            public void execute(String payload) {
                UploadConfirmDto task = JSON.parseObject(payload, UploadConfirmDto.class);
                AssessmentCompletedEventMessage event = new AssessmentCompletedEventMessage(
                        "assessment-" + task.getAssessmentId() + "-timeout", String.valueOf(System.currentTimeMillis()),
                        task.getTaskId(), task.getUserId(), task.getProjectId(), task.getAssessmentId());
                event.setFailureStage("ASSESSMENT_TIMEOUT");
                event.setFailureDetail("Assessment exceeded the configured deadline, including upload and queue wait");
                projectService.onUploadFailure(event);
            }
        };
    }

    /**
     * 真实评估链路的执行逻辑：落库上传文件与待评估记录，并发出行为计算任务消息。
     *
     */
    public void execute(String payload) {
        UploadConfirmDto task = JSON.parseObject(payload, UploadConfirmDto.class);
        // Legacy queued payloads also obtain a stable assessment before the merge can fail.
        Long assessmentId = assessmentId(task, payload);
        com.riskwarning.common.po.report.Assessment assessment = em.find(com.riskwarning.common.po.report.Assessment.class,
                assessmentId, javax.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (assessment == null) throw new IllegalStateException("Assessment no longer exists");
        if (assessment.getStatus() == com.riskwarning.common.enums.AssessmentStatusEnum.FAILED
                || assessment.getStatus() == com.riskwarning.common.enums.AssessmentStatusEnum.ASSESSED) return;
        // Serialize against project deletion and refuse queued uploads whose project no longer exists.
        if (em.find(com.riskwarning.common.po.project.Project.class, task.getProjectId(),
                javax.persistence.LockModeType.PESSIMISTIC_READ) == null) {
            throw new IllegalStateException("Project was deleted; upload source retained");
        }
        if (task.getFiles() == null || task.getFiles().isEmpty()) {
            throw new IllegalStateException("Upload metadata missing; source input retained for recovery");
        }
        ProjectFile projectFile = ProjectFile.builder().projectId(task.getProjectId())
                .userId(task.getUserId()).filePaths(new ArrayList<>()).build();
        for (UploadFileDto file : task.getFiles()) {
            String target = new java.io.File(Constants.getPersistFileDirPath(task.getProjectId()),
                    StringUtils.generateFileName(task.getProjectId(), file.getUploadId()) + "." + file.getFileSuffix())
                    .getAbsolutePath();
            FileUtils.union(file.getFilePath(), target, false);
            projectFile.getFilePaths().add(target);
        }
        files.save(projectFile);
        kafka.sendMessage(new BehaviorProcessingTaskMessage(task.getTaskId() + "-behavior",
                String.valueOf(System.currentTimeMillis()), task.getTaskId(), task.getUserId(),
                task.getProjectId(), assessmentId, DataSourceTypeEnum.FILE_UPLOAD, projectFile.getFilePaths()));
    }

    private Long assessmentId(UploadConfirmDto task, String payload) {
        if (task.getTaskId() == null) task.setTaskId(UUID.nameUUIDFromBytes(payload.getBytes(StandardCharsets.UTF_8)).toString());
        return task.getAssessmentId() != null ? task.getAssessmentId() : projectService.prepareUploadAssessment(task.getProjectId(), task.getTaskId());
    }

    @Override
    public void onExhausted(String payload, Throwable failure) {
        UploadConfirmDto task = JSON.parseObject(payload, UploadConfirmDto.class);
        if (em.find(com.riskwarning.common.po.project.Project.class, task.getProjectId()) == null) {
            log.error("Upload failed for deleted project; error retained in assessment_work: projectId={}", task.getProjectId());
            return;
        }
        Long assessmentId = assessmentId(task, payload);
        AssessmentCompletedEventMessage event =
                new AssessmentCompletedEventMessage("assessment-" + assessmentId + "-merge-failed",
                        String.valueOf(System.currentTimeMillis()), task.getTaskId(), task.getUserId(), task.getProjectId(), assessmentId);
        event.setFailureStage("FILE_MERGE");
        java.io.StringWriter trace = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(trace));
        event.setFailureDetail(trace.toString());
        projectService.onUploadFailure(event);
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
