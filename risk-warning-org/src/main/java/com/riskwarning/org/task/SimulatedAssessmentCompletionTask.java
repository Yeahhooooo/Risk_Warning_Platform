package com.riskwarning.org.task;

import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.constants.Constants;
import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.common.enums.project.ProjectStatus;
import com.riskwarning.common.hardcode.FixedAssessmentData;
import com.riskwarning.common.message.NotificationMessage;
import com.riskwarning.common.po.file.ProjectFile;
import com.riskwarning.common.po.project.Project;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.reliability.DurableWorkHandler;
import com.riskwarning.common.utils.FileUtils;
import com.riskwarning.common.utils.KafkaUtils;
import com.riskwarning.common.utils.StringUtils;
import com.riskwarning.org.entity.dto.UploadConfirmDto;
import com.riskwarning.org.entity.dto.UploadFileDto;
import com.riskwarning.org.repository.AssessmentRepository;
import com.riskwarning.org.repository.FileRepository;
import com.riskwarning.org.repository.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 演示模式下的“模拟评估”处理器。
 *
 * <p>接管 {@link ExecuteQueueTask#kind()} 对应的 {@code UPLOAD_INBOX} 工作项，
 * 替代原本“上传确认 → Kafka → processing 计算 → report 汇总”的真实评估链路：</p>
 *
 * <ol>
 *   <li>确认上传后立即落库 {@link ProjectFile} 与待评估的 {@link Assessment}（与真实链路一致）；</li>
 *   <li>不发送任何计算任务消息，因此 processing / report 完全不参与；</li>
 *   <li>等待 {@code org.assessment.simulate.delay-seconds} 秒后，直接把评估与项目标记为完成，
 *       并写入 {@link FixedAssessmentData} 的固定评估结论（分数、风险等级、详情、建议）；</li>
 *   <li>向 Kafka {@code notification_tasks} 投递评估完成通知，由 notification 服务经 WebSocket 推送前端。</li>
 * </ol>
 *
 * <p>前端随后调用 report / org 的查询接口即可拿到模拟评估数据。</p>
 *
 * <p>通过 {@code org.assessment.simulate.enabled=false} 可回退到原有的真实评估流程。</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "org.assessment.simulate.enabled", havingValue = "true", matchIfMissing = true)
public class SimulatedAssessmentCompletionTask implements DurableWorkHandler {

    private final FileRepository files;
    private final AssessmentRepository assessments;
    private final ProjectRepository projects;
    private final KafkaUtils kafka;

    private final long delaySeconds;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "simulated-assessment-completion");
        thread.setDaemon(true);
        return thread;
    });

    public SimulatedAssessmentCompletionTask(FileRepository files,
                                             AssessmentRepository assessments,
                                             ProjectRepository projects,
                                             KafkaUtils kafka,
                                             @Value("${org.assessment.simulate.delay-seconds:"
                                                     + "#{T(com.riskwarning.common.constants.Constants).SIMULATED_ASSESSMENT_DELAY_SECONDS}}")
                                             long delaySeconds) {
        this.files = files;
        this.assessments = assessments;
        this.projects = projects;
        this.kafka = kafka;
        this.delaySeconds = delaySeconds;
    }

    @Override
    public String kind() {
        return "UPLOAD_INBOX";
    }

    /** 模拟流程只保留落库与延迟完成两步；失败重试次数与真实链路保持一致。 */
    @Override
    public int maxAttempts() {
        return Constants.UPLOAD_CONFIRM_RETRY_LIMIT;
    }

    /**
     * 重试耗尽后把评估标记为失败，避免前端一直停在「待评估」。
     * 项目状态保持进行中，便于人工重新触发。
     */
    @Override
    public void onExhausted(String payload) {
        try {
            UploadConfirmDto task = JSON.parseObject(payload, UploadConfirmDto.class);
            Assessment assessment = assessments.findByProjectId(task.getProjectId());
            if (assessment != null && assessment.getStatus() != AssessmentStatusEnum.ASSESSED) {
                assessment.setStatus(AssessmentStatusEnum.FAILED);
                assessments.saveAndFlush(assessment);
                log.error("[AssessmentFlow] stage=SIMULATED_ASSESSMENT status=EXHAUSTED_FAILED projectId={} assessmentId={}",
                        task.getProjectId(), assessment.getId());
            }
        } catch (Exception e) {
            log.error("标记模拟评估失败状态时出错，payload 已保留", e);
        }
    }

    /**
     * 落库上传文件与待评估记录，并安排延迟完成。
     *
     * <p>与真实链路保持一致：文件先合并落地到 persist 目录，再写入 {@link ProjectFile}，
     * 并创建一条 {@link AssessmentStatusEnum#TO_BE_ASSESSED} 状态的评估记录，
     * 让前端在等待期间能查到一个「待评估/评估中」的记录。</p>
     */
    @Transactional
    public void execute(String payload) {
        UploadConfirmDto task = JSON.parseObject(payload, UploadConfirmDto.class);
        if (task.getFiles() == null || task.getFiles().isEmpty()) {
            throw new IllegalStateException("Upload metadata missing; source input retained for recovery");
        }

        ProjectFile projectFile = ProjectFile.builder()
                .projectId(task.getProjectId())
                .userId(task.getUserId())
                .filePaths(new ArrayList<>())
                .build();
        for (UploadFileDto file : task.getFiles()) {
            String target = Constants.getPersistFileDirPath(task.getProjectId())
                    + StringUtils.generateFileName(task.getProjectId(), file.getUploadId()) + "." + file.getFileSuffix();
            FileUtils.union(file.getFilePath(), target, false);
            projectFile.getFilePaths().add(target);
        }
        files.save(projectFile);

        Assessment assessment = Assessment.builder()
                .projectId(task.getProjectId())
                .status(AssessmentStatusEnum.TO_BE_ASSESSED)
                .createdAt(LocalDateTime.now())
                .build();
        assessments.saveAndFlush(assessment);

        Long assessmentId = assessment.getId();
        Long projectId = task.getProjectId();
        Long userId = task.getUserId();
        log.info("[AssessmentFlow] stage=SIMULATED_ASSESSMENT status=SCHEDULED projectId={} assessmentId={} userId={} delaySeconds={}",
                projectId, assessmentId, userId, delaySeconds);

        scheduleCompletion(projectId, assessmentId, userId);
    }

    private void scheduleCompletion(Long projectId, Long assessmentId, Long userId) {
        long delay = Math.max(0L, delaySeconds);
        try {
            executor.schedule(() -> {
                try {
                    complete(projectId, assessmentId, userId);
                } catch (Exception failure) {
                    // 延迟完成失败不影响已落库的上传结果；保留日志便于排查。
                    log.error("[AssessmentFlow] stage=SIMULATED_ASSESSMENT status=FAILED projectId={} assessmentId={}",
                            projectId, assessmentId, failure);
                }
            }, delay, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("模拟评估延迟任务提交失败，评估将保持待评估状态，projectId={} assessmentId={}", projectId, assessmentId, e);
        }
    }

    /**
     * 延迟到期：把评估与项目标记为完成，并推送通知。
     *
     * <p>评估结论直接采用 {@link FixedAssessmentData}（固定演示数据集），
     * 与 report 三个接口、org 企业接口返回的内容完全一致，因此前端拿到的是同一份模拟数据。</p>
     *
     * <p>注意：这里**只有状态、分数、详情、建议**采用固定数据，
     * {@code id / projectId / assessmentDate} 仍使用本次触发生成的真实记录，
     * 从而让前端按真实 assessmentId 查询时能命中固定结论。</p>
     */
    @Transactional
    public void complete(Long projectId, Long assessmentId, Long userId) {
        // Assessment 主键是 Long，仓储泛型也已对齐为 Long（曾因泛型写成 Integer 导致类型不匹配）。
        Assessment assessment = assessments.findById(assessmentId)
                .orElseThrow(() -> new IllegalStateException("Assessment not found: " + assessmentId));

        Assessment fixed = FixedAssessmentData.assessment();
        assessment.setStatus(AssessmentStatusEnum.ASSESSED);
        assessment.setAssessmentDate(LocalDateTime.now());
        assessment.setOverallScore(FixedAssessmentData.overallScore());
        assessment.setOverallRiskLevel(FixedAssessmentData.overallRiskLevel());
        assessment.setDetails(fixed.getDetails());
        assessment.setRecommendations(fixed.getRecommendations());
        assessments.saveAndFlush(assessment);

        Project project = projects.findById(projectId)
                .orElseThrow(() -> new IllegalStateException("Project not found: " + projectId));
        project.setStatus(ProjectStatus.COMPLETED);
        project.setActualCompletionDate(LocalDate.now());
        project.setUpdatedAt(LocalDateTime.now());
        projects.saveAndFlush(project);

        log.info("[AssessmentFlow] stage=SIMULATED_ASSESSMENT status=COMPLETED projectId={} assessmentId={} overallScore={} overallRiskLevel={}",
                projectId, assessmentId, assessment.getOverallScore(), assessment.getOverallRiskLevel());

        sendAssessmentCompletedNotification(userId, projectId, assessmentId, assessment);
    }

    /** 发送评估完成通知：与 report 服务同一条链路（Kafka → notification → WebSocket）。 */
    private void sendAssessmentCompletedNotification(Long userId, Long projectId, Long assessmentId, Assessment assessment) {
        if (userId == null) {
            log.warn("[AssessmentFlow] stage=SIMULATED_ASSESSMENT status=NOTIFY_SKIPPED reason=missing-userId projectId={} assessmentId={}",
                    projectId, assessmentId);
            return;
        }
        NotificationMessage notification = new NotificationMessage();
        notification.setMessageId(UUID.randomUUID().toString());
        notification.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        notification.setTraceId(UUID.randomUUID().toString());
        notification.setUserId(userId);
        notification.setProjectId(projectId);
        notification.setAssessmentId(assessmentId);
        notification.setNotificationType(NotificationMessage.NotificationType.ASSESSMENT_COMPLETED);
        notification.setTitle("评估完成通知");
        notification.setContent(String.format("项目评估已完成，总体风险等级：%s，评估得分：%.2f",
                assessment.getOverallRiskLevel().getDescription(),
                assessment.getOverallScore()));
        notification.setExtraData(JSON.toJSONString(new AssessmentCompletedData(
                assessmentId,
                projectId,
                assessment.getOverallRiskLevel().name(),
                assessment.getOverallScore())));

        kafka.sendMessage(notification);
        log.info("[AssessmentFlow] stage=SIMULATED_ASSESSMENT status=NOTIFIED projectId={} assessmentId={} userId={}",
                projectId, assessmentId, userId);
    }

    /** WebSocket 通知 extraData 结构，字段口径与 report 保持一致。 */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class AssessmentCompletedData {
        private Long assessmentId;
        private Long projectId;
        private String riskLevel;
        private Double overallScore;
    }
}
