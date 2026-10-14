package com.riskwarning.org.service.impl;
import com.alibaba.fastjson2.JSON;
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
        return AssessmentDataGuard.requireReal(assessmentRepository.findFirstByProjectIdOrderByIdDesc(projectId)
                .orElseThrow(() -> new IllegalArgumentException("assessment not found for project: " + projectId)));
    }

    @Override
    @Transactional(readOnly = true)
    public Project getProject(Long id) {
        Project project = project(id, false);
        if (projectMember(id) == null && enterpriseMember(project.getEnterpriseId()) == null)
            throw new BusinessException("无权查看该项目");
        return project;
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
                .setParameter("id", id).setParameter("states", Arrays.asList(AssessmentStatusEnum.TO_BE_ASSESSED, AssessmentStatusEnum.ASSESSING))
                .getSingleResult();
        if (active > 0) throw new BusinessException("项目正在评估，请等待完成后再修改或删除");
        Number pending = (Number) em.createNativeQuery("select count(*) from assessment_work where state='READY' "
                + "and kind <> 'PROJECT_RESOURCE_CLEANUP' and (cast(payload as jsonb)->>'projectId' = :id "
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
