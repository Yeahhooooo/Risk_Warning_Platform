package com.riskwarning.report.service.impl;

import com.riskwarning.common.context.UserContext;
import com.riskwarning.common.exception.BusinessException;
import com.riskwarning.common.po.project.ProjectMemberId;
import com.riskwarning.common.po.project.ProjectMember;
import com.riskwarning.common.po.enterprise.EnterpriseUserId;
import com.riskwarning.common.po.enterprise.EnterpriseUser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.persistence.LockModeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.config.ElasticSearchConfig;
import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.common.enums.project.ProjectStatus;
import com.riskwarning.common.enums.indicator.IndicatorRiskStatus;
import com.riskwarning.common.enums.risk.RiskLevelEnum;
import com.riskwarning.common.enums.risk.RiskStatusEnum;
import com.riskwarning.common.message.NotificationMessage;
import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.po.project.Project;
import com.riskwarning.common.po.risk.Risk;
import com.riskwarning.common.utils.KafkaUtils;
import com.riskwarning.report.entity.vo.AssessmentGeneralDetails;
import com.riskwarning.report.repository.AssessmentRepository;
import com.riskwarning.report.repository.ProjectRepository;
import com.riskwarning.report.repository.IndicatorResultRepository;
import com.riskwarning.report.service.AssessmentService;
import com.riskwarning.report.service.ReportService;
import com.riskwarning.report.util.AssessmentScores;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class AssessmentServiceImpl implements AssessmentService {

    @PersistenceContext
    private EntityManager em;



    @Autowired
    private ReportService reportService;

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private IndicatorResultRepository indicatorResultRepository;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private KafkaUtils kafkaUtils;

    private final static Double THRESHOLD_RATIO = 0.5;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAssessmentStatus(Long assessmentId) {
        if (UserContext.getUser() == null)
            throw new BusinessException(401, "请先登录");
        Assessment assessment = em.find(Assessment.class, assessmentId);
        if (assessment == null) throw new BusinessException(404, "评估记录不存在");
        Project project = em.find(Project.class, assessment.getProjectId());
        if (project == null) throw new BusinessException(404, "项目不存在");
        Long userId = UserContext.getUser().getId();
        Object member = em.find(ProjectMember.class,
                new ProjectMemberId(project.getId(), userId));
        Object enterpriseMember = em.find(EnterpriseUser.class,
                new EnterpriseUserId(project.getEnterpriseId(), userId));
        if (member == null && enterpriseMember == null)
            throw new BusinessException(403, "无权查看该项目的评估");
        return statusData(assessment);
    }

    private Map<String, Object> statusData(Assessment assessment) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assessmentId", assessment.getId());
        result.put("projectId", assessment.getProjectId());
        result.put("status", assessment.getStatus() == null ? null : assessment.getStatus().name());
        result.put("failureCode", assessment.getFailureCode());
        result.put("failureReason", assessment.getFailureReason());
        result.put("failureStage", assessment.getFailureStage());
        result.put("failedAt", assessment.getFailedAt());
        return result;
    }

    /** Joins the report worker's transaction: failure, notification and task completion commit together. */
    @Override
    @Transactional
    public void markAssessmentFailed(Long userId, Long projectId, Long assessmentId, String stage, String detail) {
        Assessment assessment = em.find(Assessment.class, assessmentId, LockModeType.PESSIMISTIC_WRITE);
        if (assessment == null || !Objects.equals(projectId, assessment.getProjectId()))
            throw new IllegalArgumentException("Assessment does not belong to project");
        if (assessment.getStatus() == AssessmentStatusEnum.ASSESSED || assessment.getStatus() == AssessmentStatusEnum.FAILED) return;
        String reason;
        switch (stage) {
            case "FILE_MERGE": reason = "上传文件合并失败，请检查文件是否完整后重新上传"; break;
            case "BEHAVIOR_PROCESSING": reason = "材料解析或行为分析失败，请检查材料或稍后重新评估"; break;
            case "INDICATOR_CALCULATION": reason = "指标计算失败，请稍后重新评估"; break;
            case "REPORT_AGGREGATION": reason = "评估报告汇总失败，请稍后重新评估"; break;
            default: throw new IllegalArgumentException("Unknown assessment failure stage: " + stage);
        }
        assessment.setStatus(AssessmentStatusEnum.FAILED);
        assessment.setFailureCode(stage + "_FAILED");
        assessment.setFailureStage(stage);
        assessment.setFailureReason(reason);
        assessment.setFailedAt(LocalDateTime.now());
        NotificationMessage notification = new NotificationMessage("assessment-" + assessmentId + "-failed",
                assessment.getFailedAt().toString(), "assessment-" + assessmentId, userId, projectId, assessmentId);
        notification.setNotificationType(NotificationMessage.NotificationType.ASSESSMENT_FAILED);
        notification.setTitle("评估失败通知");
        notification.setContent(reason);
        notification.setExtraData(JSON.toJSONString(statusData(assessment)));
        kafkaUtils.sendMessage(notification);
        em.flush();
        log.error("Assessment failed: assessmentId={}, projectId={}, stage={}, detail={}", assessmentId, projectId, stage, detail);
    }

    @Override
    @Transactional
    public void aggregateInformation(Long userId, Long projectId, Long assessmentId) {
        Assessment assessment = em.find(Assessment.class, assessmentId, LockModeType.PESSIMISTIC_WRITE);
        if (assessment == null) {
            throw new RuntimeException("Assessment not found");
        }
        if (!Objects.equals(projectId, assessment.getProjectId()))
            throw new IllegalArgumentException("Assessment project mismatch");
        if (assessment.getStatus() == AssessmentStatusEnum.FAILED || assessment.getStatus() == AssessmentStatusEnum.ASSESSED) return;
        // todo：查询IndicatorResult表，获取所有指标结果，计算结果小于maxScore * 0.5的产生风险，预留处置接口
        List<IndicatorResult> indicatorResults = indicatorResultRepository.findByAssessmentId(assessmentId);
        if (indicatorResults.isEmpty()) {
            throw new IllegalStateException("No indicator results for assessmentId: " + assessmentId);
        }
        List<Risk> risks = new ArrayList<>();
        Double totalScore = AssessmentScores.percentage(indicatorResults);
        if (totalScore == null) throw new IllegalStateException("No valid indicator scores for assessmentId: " + assessmentId);
        int lowRiskCount = 0, mediumRiskCount = 0, highRiskCount = 0, totalRiskCount = 0;
        for(IndicatorResult ir : indicatorResults){
            Double scoreRatio = AssessmentScores.ratio(ir);
            if (scoreRatio == null) continue;
            if(scoreRatio < THRESHOLD_RATIO) {
                totalRiskCount++;
                RiskLevelEnum riskLevelEnum = RiskLevelEnum.getByScoreRatio(scoreRatio);
                lowRiskCount += riskLevelEnum == RiskLevelEnum.LOW_RISK ? 1 : 0;
                mediumRiskCount += riskLevelEnum == RiskLevelEnum.MEDIUM_RISK ? 1 : 0;
                highRiskCount += riskLevelEnum == RiskLevelEnum.HIGH_RISK ? 1 : 0;
                ir.setRiskTriggered(true);
                ir.setRiskStatus(IndicatorRiskStatus.EVALUATED);
                String indicatorName = ir.getIndicatorName();
                Risk risk = Risk.builder()
                        .projectId(projectId)
                        .assessmentId(assessmentId)
                        .name("指标风险-" + (indicatorName != null && !indicatorName.isEmpty() ? indicatorName : "未命名指标"))
                        .dimension(ir.getDimension())
                        .description("")
                        .riskLevel(riskLevelEnum)
                        .probability(0.0)
                        .impact(0.0)
                        .detectability(0.0)
                        .status(RiskStatusEnum.TO_BE_DISPOSED)
                        .processingStatus("成功")
                        .responsibleParty("")
                        .affectedObjects(new String[]{})
                        .impactScope("")
                        .countermeasures("")
                        .relatedIndicators(ir.getCalculationDetails().getRelatedIndicators())
                        .createAt(LocalDateTime.now())
                        .build();
                risks.add(risk);
            }
        }

        // 持久化指标结果的“触发风险”状态，供指标概览/分布统计使用
        indicatorResultRepository.saveAll(indicatorResults);

        if (!risks.isEmpty()) {
            try {
                BulkResponse response = elasticsearchClient.bulk(b -> {
                    for (Risk risk : risks) {
                        b.operations(op -> op
                                .index(idx -> idx
                                        .index(ElasticSearchConfig.RISK_INDEX)
                                        .document(risk)
                                )
                        );
                    }
                    return b;
                });
                if (response.errors()) {
                    log.error("Bulk insert encountered errors: {}", response.items().toString());
                    throw new Exception("Bulk insert to Elasticsearch failed");
                }
            } catch (Exception e) {
                log.error("Failed to index risk documents to Elasticsearch: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }

        assessment.setOverallScore(totalScore);
//            // TODO: 总风险等级依靠owRiskCount, mediumRiskCoun, highRiskCount来设置
        assessment.setOverallRiskLevel(RiskLevelEnum.getByRiskCount(lowRiskCount, mediumRiskCount, highRiskCount));
        assessment.setStatus(AssessmentStatusEnum.ASSESSED);
        assessment.setFailureCode(null);
        assessment.setFailureReason(null);
        assessment.setFailureStage(null);
        assessment.setFailedAt(null);
        assessment.setAssessmentDate(LocalDateTime.now());
        assessment.setDetails(JSON.toJSONString(new AssessmentGeneralDetails(
                reportService.assembleGeneral(assessment, risks),
                reportService.assembleIndicatorResult(assessment)
        )));
        // 汇总报告完成后立即刷新到数据库；这也是完成状态的最后一道确认。
        assessmentRepository.saveAndFlush(assessment);
        log.info("评估完成状态已持久化，assessmentId={}, status={}", assessmentId, assessment.getStatus());

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));
        project.setStatus(ProjectStatus.COMPLETED);
        project.setActualCompletionDate(java.time.LocalDate.now());
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.saveAndFlush(project);
        log.info("项目完成状态已持久化，projectId={}, status={}", projectId, project.getStatus());

        // 发送通知消息，通知前端评估完成
        sendAssessmentCompletedNotification(userId, projectId, assessmentId, assessment);
    }

    /**
     * 发送评估完成通知
     */
    private void sendAssessmentCompletedNotification(Long userId, Long projectId, Long assessmentId, Assessment assessment) {
        try {
            NotificationMessage notificationMessage = new NotificationMessage();
            notificationMessage.setMessageId(UUID.randomUUID().toString());
            notificationMessage.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            notificationMessage.setTraceId(UUID.randomUUID().toString());
            notificationMessage.setUserId(userId);
            notificationMessage.setProjectId(projectId);
            notificationMessage.setAssessmentId(assessmentId);
            notificationMessage.setNotificationType(NotificationMessage.NotificationType.ASSESSMENT_COMPLETED);
            notificationMessage.setTitle("评估完成通知");
            notificationMessage.setContent(String.format("项目评估已完成，总体风险等级：%s，评估得分：%.2f",
                    assessment.getOverallRiskLevel().getDescription(),
                    assessment.getOverallScore()));
            notificationMessage.setExtraData(JSON.toJSONString(new AssessmentNotificationData(
                    assessmentId,
                    projectId,
                    assessment.getOverallRiskLevel().name(),
                    assessment.getOverallScore()
            )));

            kafkaUtils.sendMessage(notificationMessage);
            log.info("评估完成通知已发送，assessmentId: {}, userId: {}", assessmentId, userId);
        } catch (Exception e) {
            log.error("发送评估完成通知失败: {}", e.getMessage());
            throw new IllegalStateException("无法持久化评估完成通知", e);
        }
    }

    /**
     * 评估通知数据
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class AssessmentNotificationData {
        private Long assessmentId;
        private Long projectId;
        private String riskLevel;
        private Double overallScore;
    }
}
