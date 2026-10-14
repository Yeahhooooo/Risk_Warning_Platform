package com.riskwarning.org.service.impl;
import com.riskwarning.common.dto.enterprise.EnterpriseUpdateRequest;
import com.riskwarning.common.exception.BusinessException;
import com.riskwarning.org.service.ProjectService;
import javax.persistence.LockModeType;
import java.time.LocalDate;

import com.riskwarning.common.context.UserContext;
import com.riskwarning.common.dto.UserResponse;
import com.riskwarning.common.dto.enterprise.EnterpriseUserResponse;
import com.riskwarning.common.enums.EnterpriseRole;
import com.riskwarning.common.po.enterprise.Enterprise;
import com.riskwarning.common.po.enterprise.EnterpriseUser;
import com.riskwarning.common.po.enterprise.EnterpriseUserId;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.po.project.Project;
import com.riskwarning.common.utils.AssessmentDataGuard;
import com.riskwarning.common.po.user.User;
import com.riskwarning.org.repository.AssessmentRepository;
import com.riskwarning.org.repository.EnterpriseRepository;
import com.riskwarning.org.repository.EnterpriseUserRepository;
import com.riskwarning.org.repository.ProjectRepository;
import com.riskwarning.org.service.EnterpriseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EnterpriseServiceImpl implements EnterpriseService {
    @org.springframework.beans.factory.annotation.Autowired
    private ProjectService projectService;


    private final EnterpriseRepository enterpriseRepository;
    private final EnterpriseUserRepository enterpriseUserRepository;
    private final ProjectRepository projectRepository;
    private final AssessmentRepository assessmentRepository;
    private final EntityManager em;

    public EnterpriseServiceImpl(EnterpriseRepository enterpriseRepository,
                                 EnterpriseUserRepository enterpriseUserRepository,
                                 ProjectRepository projectRepository,
                                 AssessmentRepository assessmentRepository,
                                 EntityManager em) {
        this.enterpriseRepository = enterpriseRepository;
        this.enterpriseUserRepository = enterpriseUserRepository;
        this.projectRepository = projectRepository;
        this.assessmentRepository = assessmentRepository;
        this.em = em;
    }

    @Override
    @Transactional
    public Enterprise createEnterprise(Enterprise enterprise) {
        String creditCode = enterprise.getCreditCode();

        if (creditCode != null && enterpriseRepository.findByCreditCode(creditCode).isPresent()) {
            throw new IllegalArgumentException("creditCode already exists: " + creditCode);
        }

        LocalDateTime now = LocalDateTime.now();
        enterprise.setCreatedAt(now);
        enterprise.setUpdatedAt(now);
        Enterprise saved = enterpriseRepository.save(enterprise);

        User currentUser = UserContext.getUser();
        Long ownerUserId = currentUser.getId();

        if (ownerUserId != null) {
            User owner = em.find(User.class, ownerUserId);
            if (owner == null) {
                throw new IllegalArgumentException("owner user not found: " + ownerUserId);
            }

            EnterpriseUserId id = new EnterpriseUserId();
            id.setEnterpriseId(saved.getId());
            id.setUserId(ownerUserId);

            EnterpriseUser eu = new EnterpriseUser();
            eu.setId(id);
            eu.setEnterprise(saved);
            eu.setUser(owner);
            eu.setRole(EnterpriseRole.admin);

            enterpriseUserRepository.save(eu);
        }

        return saved;
    }

    @Override
    @Transactional
    public void addUserToEnterprise(Long enterpriseId, Long userId, String role) {
        // 确保企业存在
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new IllegalArgumentException("enterprise not found: " + enterpriseId));

        // 获取 user 引用（如果不存在会返回 null）
        User user = em.find(User.class, userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found: " + userId);
        }
        EnterpriseRole roleEnum = EnterpriseRole.from(role);

        EnterpriseUserId id = new EnterpriseUserId();
        id.setEnterpriseId(enterpriseId);
        id.setUserId(userId);

        EnterpriseUser eu = new EnterpriseUser();
        eu.setId(id);
        eu.setEnterprise(enterprise);
        eu.setUser(user);
        eu.setRole(roleEnum);

        enterpriseUserRepository.save(eu);
    }


    @Override
    @Transactional
    public java.util.List<Enterprise> getAllEnterprises() {
        return enterpriseRepository.findAll();
    }


    // java
    @Override
    @Transactional
    public List<EnterpriseUserResponse> getUsersByEnterprise(Long enterpriseId) {
        enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new IllegalArgumentException("enterprise not found: " + enterpriseId));

        List<EnterpriseUser> all = enterpriseUserRepository.findAll();

        return all.stream()
                .filter(eu -> {
                    if (eu == null) return false;
                    // 尝试通过关联实体判断；若 enterprise 为 null 则退到 embedded id 判断
                    if (eu.getEnterprise() != null) {
                        return enterpriseId.equals(eu.getEnterprise().getId());
                    }
                    EnterpriseUserId id = eu.getId();
                    return id != null && enterpriseId.equals(id.getEnterpriseId());
                })
                .map(eu -> {
                    User u = eu.getUser();
                    UserResponse ur = new UserResponse(
                            u.getId(),
                            u.getEmail(),
                            u.getFullName(),
                            u.getAvatarUrl(),
                            u.getCreatedAt(),
                            u.getUpdatedAt()
                    );
                    String roleCode = eu.getRole() == null ? null : eu.getRole().toString();
                    return new EnterpriseUserResponse(ur, roleCode);
                })
                .collect(Collectors.toList());
    }

    /** Return the latest assessment across this enterprise's projects. */
    @Override
    @Transactional(readOnly = true)
    public Assessment getLatestAssessmentByEnterpriseName(String name) {
        Enterprise enterprise = enterpriseRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("enterprise not found: " + name));
        List<Long> projectIds = projectRepository.findByEnterpriseId(enterprise.getId()).stream()
                .map(Project::getId).collect(Collectors.toList());
        if (projectIds.isEmpty()) return null;
        return assessmentRepository.findFirstByProjectIdInOrderByIdDesc(projectIds)
                .map(AssessmentDataGuard::requireReal).orElse(null);
    }


    @Override
    @Transactional(readOnly = true)
    public Enterprise getEnterprise(Long id) {
        Enterprise enterprise = enterprise(id, false);
        if (enterpriseMember(id) == null) throw new BusinessException("无权查看该企业");
        return enterprise;
    }

    @Override
    @Transactional
    public Enterprise updateEnterprise(Long id, EnterpriseUpdateRequest request) {
        Enterprise enterprise = enterprise(id, true);
        requireEnterpriseAdmin(id);
        if (request == null) throw new BusinessException("修改内容不能为空");
        if (request.getName() != null) enterprise.setName(nonBlank(request.getName(), "企业名称"));
        if (request.getCreditCode() != null) {
            String code = nonBlank(request.getCreditCode(), "统一社会信用代码");
            enterpriseRepository.findByCreditCode(code).filter(other -> !id.equals(other.getId())).ifPresent(other -> {
                throw new BusinessException("统一社会信用代码已被其他企业使用");
            });
            enterprise.setCreditCode(code);
        }
        if (request.getRegisteredCapital() != null) {
            if (request.getRegisteredCapital().signum() < 0) throw new BusinessException("注册资本不能为负数");
            enterprise.setRegisteredCapital(request.getRegisteredCapital());
        }
        if (request.getEstablishmentDate() != null) {
            if (request.getEstablishmentDate().isAfter(LocalDate.now())) throw new BusinessException("成立日期不能晚于今天");
            enterprise.setEstablishmentDate(request.getEstablishmentDate());
        }
        if (request.getType() != null) enterprise.setType(request.getType());
        if (request.getIndustry() != null) enterprise.setIndustry(request.getIndustry());
        if (request.getBusinessScope() != null) enterprise.setBusinessScope(request.getBusinessScope());
        if (request.getLegalRepresentative() != null) enterprise.setLegalRepresentative(request.getLegalRepresentative());
        if (request.getRegisteredAddress() != null) enterprise.setRegisteredAddress(request.getRegisteredAddress());
        if (request.getBusinessStatus() != null) enterprise.setBusinessStatus(request.getBusinessStatus());
        enterprise.setUpdatedAt(LocalDateTime.now());
        return enterprise;
    }

    private Enterprise enterprise(Long id, boolean lock) {
        positive(id);
        Enterprise entity = lock ? em.find(Enterprise.class, id, LockModeType.PESSIMISTIC_WRITE) : em.find(Enterprise.class, id);
        if (entity == null) throw new BusinessException("企业不存在");
        return entity;
    }

    private void requireEnterpriseAdmin(Long id) {
        EnterpriseUser member = enterpriseMember(id);
        if (member == null || member.getRole() != EnterpriseRole.admin) throw new BusinessException("仅企业管理员可修改或删除企业");
    }

    @Override
    @Transactional
    public void deleteEnterprise(Long id) {
        Enterprise enterprise = enterprise(id, true);
        requireEnterpriseAdmin(id);
        projectService.deleteProjectsByEnterprise(id);
        em.createQuery("delete from EnterpriseUser m where m.id.enterpriseId = :id").setParameter("id", id).executeUpdate();
        em.remove(enterprise);
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
