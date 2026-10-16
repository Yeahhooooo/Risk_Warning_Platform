package com.riskwarning.org.service.impl;
import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.utils.KafkaUtils;
import com.riskwarning.common.message.NotificationMessage;
import com.riskwarning.common.message.AssessmentCompletedEventMessage;
import com.riskwarning.org.entity.dto.UploadConfirmDto;
import com.riskwarning.common.dto.project.ProjectUpdateRequest;
import com.riskwarning.common.enums.*;
import com.riskwarning.common.enums.project.*;
import com.riskwarning.common.exception.BusinessException;
import com.riskwarning.common.po.enterprise.*;
import com.riskwarning.common.reliability.DurableWorkStore;
import javax.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import com.riskwarning.common.context.UserContext;
import com.riskwarning.common.dto.UserResponse;
import com.riskwarning.common.dto.project.ProjectMemberResponse;
import com.riskwarning.common.po.project.Project;
import com.riskwarning.common.po.project.ProjectMember;
import com.riskwarning.common.po.project.ProjectMemberId;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.org.repository.ProjectMemberRepository;
import com.riskwarning.org.repository.ProjectRepository;
import com.riskwarning.org.repository.AssessmentRepository;
import com.riskwarning.common.utils.AssessmentDataGuard;
import com.riskwarning.org.service.ProjectService;
import com.riskwarning.common.po.user.User;
import com.riskwarning.common.enums.project.ProjectRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProjectServiceImpl implements ProjectService {
    @org.springframework.beans.factory.annotation.Autowired
    private DurableWorkStore work;

    @org.springframework.beans.factory.annotation.Autowired
    private KafkaUtils kafka;

    @org.springframework.beans.factory.annotation.Value("${org.upload.assessment-timeout-seconds:1800}")
    private long assessmentTimeoutSeconds = 1800;


    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final EntityManager em;
    private final AssessmentRepository assessmentRepository;

    public ProjectServiceImpl(ProjectRepository projectRepository,
                              ProjectMemberRepository projectMemberRepository,
                              EntityManager em, AssessmentRepository assessmentRepository) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.em = em;
        this.assessmentRepository = assessmentRepository;
    }

    @Override
    @Transactional
    public Project createProject(Project project) {
        if (project.getEnterpriseId() == null || em.find(com.riskwarning.common.po.enterprise.Enterprise.class,
                project.getEnterpriseId(), javax.persistence.LockModeType.PESSIMISTIC_READ) == null) {
            throw new IllegalArgumentException("enterprise not found: " + project.getEnterpriseId());
        }
        LocalDateTime now = LocalDateTime.now();
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        Project saved = projectRepository.save(project);

        User currentUser = UserContext.getUser();
        Long ownerUserId = currentUser == null ? null : currentUser.getId();
        if (ownerUserId != null) {
            User owner = em.find(User.class, ownerUserId);
            if (owner == null) {
                throw new IllegalArgumentException("owner user not found: " + ownerUserId);
            }

            ProjectMemberId id = new ProjectMemberId();
            id.setProjectId(saved.getId());
            id.setUserId(ownerUserId);


            ProjectMember pm = new ProjectMember();
            pm.setId(id);
            pm.setProject(saved);
            pm.setUser(owner);
            pm.setRole(ProjectRole.PROJECT_ADMIN);
            projectMemberRepository.save(pm);
        }

        return saved;
    }

    @Override
    @Transactional
    public void addUserToProject(Long projectId, Long userId, String role) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("project not found: " + projectId));

        User user = em.find(User.class, userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found: " + userId);
        }

        ProjectRole roleEnum = ProjectRole.from(role);

        ProjectMemberId id = new ProjectMemberId();
        id.setProjectId(projectId);
        id.setUserId(userId);

        ProjectMember pm = new ProjectMember();
        pm.setProject(project);
        pm.setUser(user);
        pm.setId(id);
        pm.setRole(roleEnum);

        projectMemberRepository.save(pm);
    }

    @Override
    @Transactional
    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    @Override
    @Transactional
    public List<ProjectMemberResponse> getProjectMembers(Long projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("project not found: " + projectId));

        List<ProjectMember> all = projectMemberRepository.findAll();

        return all.stream()
                .filter(pm -> {
                    if (pm == null) return false;
                    ProjectMemberId id = pm.getId();
                    return id != null && projectId.equals(id.getProjectId());
                })
                .map(pm -> {
                    ProjectMemberId id = pm.getId();
                    User u = em.find(User.class, id.getUserId());
                    UserResponse ur = new UserResponse(
                            u.getId(),
                            u.getEmail(),
                            u.getFullName(),
                            u.getAvatarUrl(),
                            u.getCreatedAt(),
                            u.getUpdatedAt()
                    );
                    String roleCode = pm.getRole() == null ? null : pm.getRole().toString();
                    return new ProjectMemberResponse(ur, roleCode);
                })
                .collect(Collectors.toList());
    }

    /** Return the most recently created assessment for the requested project. */
    @Override
    @Transactional(readOnly = true)
    public Assessment getAssessmentByProjectId(Long projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("project not found: " + projectId));
        // First upload has no previous assessment; callers use null as the baseline ID of zero.
        return assessmentRepository.findFirstByProjectIdOrderByIdDesc(projectId)
                .map(AssessmentDataGuard::requireReal).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Project getProject(Long id) {
        Project project = project(id, false);
        if (projectMember(id) == null && enterpriseMember(project.getEnterpriseId()) == null)
            throw new BusinessException("无权查看该项目");
        return project;
    }

    /** Commit before file merging, so upload rollback cannot erase its assessment. */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Long prepareUploadAssessment(Long projectId, String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) throw new IllegalArgumentException("Upload requires taskId");
        project(projectId, true);
        List<Assessment> rows = em.createQuery("select a from Assessment a where a.uploadTaskId = :taskId", Assessment.class)
                .setParameter("taskId", taskId).getResultList();
        if (!rows.isEmpty()) {
            if (!java.util.Objects.equals(projectId, rows.get(0).getProjectId()))
                throw new IllegalArgumentException("Upload project mismatch");
            return rows.get(0).getId();
        }
        Assessment assessment = Assessment.builder().projectId(projectId).uploadTaskId(taskId)
                .status(AssessmentStatusEnum.TO_BE_ASSESSED).createdAt(LocalDateTime.now()).build();
        assessmentRepository.saveAndFlush(assessment);
        return assessment.getId();
    }

    /** A project lock makes concurrent file initializations join the same upload batch. */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Assessment beginUploadAssessment(Long projectId) {
        requireUploadAccess(project(projectId, true));
        Assessment latest = assessmentRepository.findFirstByProjectIdOrderByIdDesc(projectId).orElse(null);
        if (latest != null && latest.getStatus() == AssessmentStatusEnum.UPLOADING) return latest;
        if (latest != null && (latest.getStatus() == AssessmentStatusEnum.TO_BE_ASSESSED
                || latest.getStatus() == AssessmentStatusEnum.ASSESSING))
            throw new BusinessException(409, "项目已有评估任务，请等待完成后再上传");
        Assessment assessment = Assessment.builder().projectId(projectId)
                .uploadTaskId(java.util.UUID.randomUUID().toString()).status(AssessmentStatusEnum.UPLOADING)
                .createdAt(LocalDateTime.now()).build();
        assessmentRepository.saveAndFlush(assessment);
        UploadConfirmDto timeout = UploadConfirmDto.builder()
                .projectId(projectId).assessmentId(assessment.getId()).taskId(assessment.getUploadTaskId())
                .userId(userId()).build();
        work.enqueueAt("ASSESSMENT_WATCHDOG", assessment.getUploadTaskId(), JSON.toJSONString(timeout),
                java.time.Instant.now().plusSeconds(assessmentTimeoutSeconds));
        return assessment;
    }

    @Override
    @Transactional(readOnly = true)
    public Assessment requireUploadingAssessment(Long projectId) {
        requireUploadAccess(project(projectId, false));
        Assessment assessment = assessmentRepository.findFirstByProjectIdOrderByIdDesc(projectId)
                .orElseThrow(() -> new BusinessException("上传评估不存在，请重新初始化上传"));
        if (assessment.getStatus() != AssessmentStatusEnum.UPLOADING)
            throw new BusinessException(409, "本次上传已结束，请查看评估状态或重新上传");
        return assessment;
    }

    private void requireUploadAccess(Project project) {
        if (projectMember(project.getId()) == null && enterpriseMember(project.getEnterpriseId()) == null)
            throw new BusinessException(403, "无权上传该项目的文件");
    }

    /** Status transition and durable submission commit together; Redis handoff is legacy only. */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void confirmUploadAssessment(UploadConfirmDto task) {
        requireUploadAccess(project(task.getProjectId(), true));
        Assessment assessment = em.find(Assessment.class, task.getAssessmentId(), LockModeType.PESSIMISTIC_WRITE);
        if (assessment == null || !java.util.Objects.equals(task.getProjectId(), assessment.getProjectId())
                || !java.util.Objects.equals(task.getTaskId(), assessment.getUploadTaskId()))
            throw new BusinessException("上传任务与评估记录不匹配");
        if (assessment.getStatus() == AssessmentStatusEnum.TO_BE_ASSESSED) return;
        if (assessment.getStatus() != AssessmentStatusEnum.UPLOADING)
            throw new BusinessException(409, "评估已结束，不能继续提交上传");
        assessment.setStatus(AssessmentStatusEnum.TO_BE_ASSESSED);
        work.enqueue("UPLOAD_INBOX", task.getTaskId(), JSON.toJSONString(task));
    }

    /** Synchronous Spring listener, independent of the failed upload transaction. */
    @Override
    @org.springframework.context.event.EventListener
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onUploadFailure(AssessmentCompletedEventMessage event) {
        if (event.getFailureStage() == null) return;
        Assessment assessment = em.find(Assessment.class, event.getAssessmentId(), LockModeType.PESSIMISTIC_WRITE);
        if (assessment == null || !java.util.Objects.equals(event.getProjectId(), assessment.getProjectId())) return;
        if (assessment.getStatus() == AssessmentStatusEnum.ASSESSED || assessment.getStatus() == AssessmentStatusEnum.FAILED) return;
        // A late chunk/confirm error cannot fail an already submitted batch.
        if (event.getFailureStage().startsWith("UPLOAD_") && assessment.getStatus() != AssessmentStatusEnum.UPLOADING) return;
        String reason;
        switch (event.getFailureStage()) {
            case "UPLOAD_INIT": reason = "文件上传初始化失败，请检查文件后重新上传"; break;
            case "UPLOAD_CHUNK": reason = "文件分片上传失败，请重新上传"; break;
            case "UPLOAD_CONFIRM": reason = "文件上传确认失败，请检查分片后重新上传"; break;
            case "UPLOAD_CANCELLED": reason = "文件上传已取消，请重新上传后评估"; break;
            case "FILE_MERGE": reason = "上传文件合并失败，请检查文件是否完整后重新上传"; break;
            case "ASSESSMENT_TIMEOUT": reason = "上传或评估超时，请检查服务后重新评估"; break;
            default: throw new IllegalArgumentException("Unknown upload failure stage: " + event.getFailureStage());
        }
        assessment.setStatus(AssessmentStatusEnum.FAILED);
        assessment.setFailureCode(event.getFailureStage() + "_FAILED");
        assessment.setFailureReason(reason);
        assessment.setFailureStage(event.getFailureStage());
        assessment.setFailedAt(LocalDateTime.now());
        // Retain the complete exception in the durable outbox payload for tracing.
        kafka.sendMessage(event);
        NotificationMessage notification = new NotificationMessage(
                "assessment-" + assessment.getId() + "-failed", assessment.getFailedAt().toString(),
                assessment.getUploadTaskId(), event.getUserId(), assessment.getProjectId(), assessment.getId());
        notification.setNotificationType(NotificationMessage.NotificationType.ASSESSMENT_FAILED);
        notification.setTitle("评估失败通知"); notification.setContent(reason);
        java.util.Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("assessmentId", assessment.getId()); status.put("projectId", assessment.getProjectId());
        status.put("status", "FAILED"); status.put("failureCode", assessment.getFailureCode());
        status.put("failureReason", reason); status.put("failureStage", assessment.getFailureStage());
        status.put("failedAt", assessment.getFailedAt());
        notification.setExtraData(JSON.toJSONString(status));
        kafka.sendMessage(notification);
        em.flush();
    }

    @Override
    @Transactional
    public Project updateProject(Long id, ProjectUpdateRequest request) {
        Project project = project(id, true);
        requireProjectAdmin(project);
        if (request == null) throw new BusinessException("修改内容不能为空");
        requireIdle(id);
        LocalDate start = request.getStartDate() == null ? project.getStartDate() : request.getStartDate();
        LocalDate end = request.getPlannedCompletionDate() == null ? project.getPlannedCompletionDate() : request.getPlannedCompletionDate();
        if (start != null && end != null && end.isBefore(start)) throw new BusinessException("计划完成日期不能早于开始日期");
        if (request.getName() != null) project.setName(nonBlank(request.getName(), "项目名称"));
        if (request.getType() != null) project.setType(ProjectType.fromCode(request.getType()));
        if (request.getIndustry() != null) project.setIndustry(IndustryEnum.fromCode(request.getIndustry()));
        if (request.getRegion() != null) project.setRegion(RegionEnum.fromCode(request.getRegion()));
        if (request.getOrientedUser() != null) project.setOrientedUser(ProjectOrientedUserEnum.fromCode(request.getOrientedUser()));
        if (request.getDescription() != null) project.setDescription(request.getDescription());
        project.setStartDate(start); project.setPlannedCompletionDate(end);
        project.setUpdatedAt(LocalDateTime.now());
        return project;
    }

    @Override
    @Transactional
    public void deleteProject(Long id) {
        Project project = project(id, true);
        requireProjectAdmin(project);
        requireIdle(id);
        removeProject(project);
    }

    private void removeProject(Project project) {
        Long id = project.getId();
        // Explicit cleanup also works when ORM-created tables lack the schema.sql cascade constraints.
        em.createQuery("delete from IndicatorResult r where r.projectId = :id").setParameter("id", id).executeUpdate();
        em.createQuery("delete from Assessment a where a.projectId = :id").setParameter("id", id).executeUpdate();
        em.createQuery("delete from ProjectFile f where f.projectId = :id").setParameter("id", id).executeUpdate();
        em.createQuery("delete from ProjectMember m where m.id.projectId = :id").setParameter("id", id).executeUpdate();
        // t_event is maintained by schema.sql and cascades with the project foreign key.
        em.remove(project);
        // ES and disk cannot join the database transaction. Persist cleanup atomically for retry after commit.
        work.enqueue("PROJECT_RESOURCE_CLEANUP", "delete-project-" + id, JSON.toJSONString(Collections.singletonMap("projectId", id)));
    }

    private void requireIdle(Long id) {
        Long active = em.createQuery("select count(a) from Assessment a where a.projectId = :id and a.status in :states", Long.class)
                .setParameter("id", id).setParameter("states", Arrays.asList(AssessmentStatusEnum.UPLOADING, AssessmentStatusEnum.TO_BE_ASSESSED, AssessmentStatusEnum.ASSESSING))
                .getSingleResult();
        if (active > 0) throw new BusinessException("项目正在评估，请等待完成后再修改或删除");
        Number pending = (Number) em.createNativeQuery("select count(*) from assessment_work where state in ('READY','FAILURE_PENDING') "
                + "and kind not in ('PROJECT_RESOURCE_CLEANUP','ASSESSMENT_WATCHDOG') and (cast(payload as jsonb)->>'projectId' = :id "
                + "or cast(payload as jsonb)->'message'->>'projectId' = :id)")
                .setParameter("id", id.toString()).getSingleResult();
        if (pending.longValue() > 0) throw new BusinessException("项目仍有待处理任务，请稍后再修改或删除");
    }

    private Project project(Long id, boolean lock) {
        positive(id);
        Project entity = lock ? em.find(Project.class, id, LockModeType.PESSIMISTIC_WRITE) : em.find(Project.class, id);
        if (entity == null) throw new BusinessException("项目不存在");
        return entity;
    }

    private ProjectMember projectMember(Long id) {
        return em.find(ProjectMember.class, new ProjectMemberId(id, userId()));
    }

    private void requireProjectAdmin(Project project) {
        EnterpriseUser enterpriseMember = enterpriseMember(project.getEnterpriseId());
        if (enterpriseMember != null && enterpriseMember.getRole() == EnterpriseRole.admin) return;
        ProjectMember member = projectMember(project.getId());
        if (member == null || member.getRole() != ProjectRole.PROJECT_ADMIN) throw new BusinessException("仅企业管理员或项目管理员可修改或删除项目");
    }

    private void requireEnterpriseAdmin(Long id) {
        EnterpriseUser member = enterpriseMember(id);
        if (member == null || member.getRole() != EnterpriseRole.admin) throw new BusinessException("仅企业管理员可修改或删除企业");
    }

    /** Called by enterprise deletion within the same transaction; authorize and validate all children first. */
    @Override
    @Transactional
    public void deleteProjectsByEnterprise(Long enterpriseId) {
        positive(enterpriseId);
        if (em.find(Enterprise.class, enterpriseId, LockModeType.PESSIMISTIC_WRITE) == null)
            throw new BusinessException("企业不存在");
        requireEnterpriseAdmin(enterpriseId);
        List<Project> projects = em.createQuery("select p from Project p where p.enterpriseId = :id order by p.id", Project.class)
                .setParameter("id", enterpriseId).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        for (Project project : projects) requireIdle(project.getId());
        for (Project project : projects) removeProject(project);
    }

    private EnterpriseUser enterpriseMember(Long id) {
        return em.find(EnterpriseUser.class, new EnterpriseUserId(id, userId()));
    }

    private Long userId() {
        User user = UserContext.getUser();
        if (user == null || user.getId() == null) throw new BusinessException("请先登录");
        return user.getId();
    }

    private void positive(Long id) {
        if (id == null || id <= 0) throw new BusinessException("ID 必须为正整数");
    }

    private String nonBlank(String value, String field) {
        if (value.trim().isEmpty()) throw new BusinessException(field + "不能为空");
        return value.trim();
    }
}
