package com.riskwarning.org.service;

import com.riskwarning.common.context.UserContext;
import com.riskwarning.common.dto.enterprise.EnterpriseUpdateRequest;
import com.riskwarning.common.dto.project.ProjectUpdateRequest;
import com.riskwarning.common.enums.EnterpriseRole;
import com.riskwarning.common.enums.project.*;
import com.riskwarning.common.exception.BusinessException;
import com.riskwarning.common.po.enterprise.*;
import com.riskwarning.common.po.project.*;
import com.riskwarning.common.po.user.User;
import com.riskwarning.common.reliability.DurableWorkStore;
import com.riskwarning.org.repository.*;
import com.riskwarning.org.service.impl.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.*;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnterpriseProjectManagementServiceTest {
    private final EntityManager em = mock(EntityManager.class);
    private final EnterpriseRepository enterprises = mock(EnterpriseRepository.class);
    private final DurableWorkStore work = mock(DurableWorkStore.class);
    private final ProjectServiceImpl projectService = new ProjectServiceImpl(mock(ProjectRepository.class),
            mock(ProjectMemberRepository.class), em, mock(AssessmentRepository.class));
    private final EnterpriseServiceImpl enterpriseService = new EnterpriseServiceImpl(enterprises,
            mock(EnterpriseUserRepository.class), mock(ProjectRepository.class), mock(AssessmentRepository.class), em);
    private final Enterprise enterprise = Enterprise.builder().id(7L).name("企业A").creditCode("original")
            .createdAt(LocalDateTime.of(2020,1,1,0,0)).build();
    private final Project project = Project.builder().id(18L).enterpriseId(7L).name("项目A")
            .status(ProjectStatus.COMPLETED).industry(IndustryEnum.values()[0])
            .startDate(LocalDate.of(2026,1,1)).plannedCompletionDate(LocalDate.of(2026,12,1)).build();
    private final Query pending = mock(Query.class);
    private final Query deletion = mock(Query.class);
    private final TypedQuery<Long> active = mock(TypedQuery.class);

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(projectService, "work", work);
        ReflectionTestUtils.setField(enterpriseService, "projectService", projectService);
        User user = new User(); user.setId(5L); UserContext.setUser(user);
        when(em.find(Enterprise.class,7L,LockModeType.PESSIMISTIC_WRITE)).thenReturn(enterprise);
        when(em.find(Project.class,18L,LockModeType.PESSIMISTIC_WRITE)).thenReturn(project);
        when(em.find(Enterprise.class,7L)).thenReturn(enterprise);
        when(em.find(Project.class,18L)).thenReturn(project);
        when(em.createQuery(anyString(),eq(Long.class))).thenReturn(active);
        when(active.setParameter(anyString(),any())).thenReturn(active);
        when(active.getSingleResult()).thenReturn(0L);
        when(em.createNativeQuery(anyString())).thenReturn(pending);
        when(pending.setParameter(anyString(),any())).thenReturn(pending);
        when(pending.getSingleResult()).thenReturn(0L);
        when(em.createQuery(anyString())).thenReturn(deletion);
        when(deletion.setParameter(anyString(),any())).thenReturn(deletion);
    }
    @AfterEach void clear() { UserContext.clear(); }

    @Test void firstUploadCreatesAssessmentAndWatchdogAndNextFileReusesIt() {
        projectRole(ProjectRole.PROJECT_ADMIN);
        when(assessmentRepository().saveAndFlush(any())).thenAnswer(call -> {
            com.riskwarning.common.po.report.Assessment row = call.getArgument(0); row.setId(12L); return row;
        });
        com.riskwarning.common.po.report.Assessment first = projectService.beginUploadAssessment(18L);
        assertEquals(com.riskwarning.common.enums.AssessmentStatusEnum.UPLOADING, first.getStatus());
        verify(work).enqueueAt(eq("ASSESSMENT_WATCHDOG"), eq(first.getUploadTaskId()), contains("\"assessmentId\":12"), any());
        when(assessmentRepository().findFirstByProjectIdOrderByIdDesc(18L)).thenReturn(Optional.of(first));
        assertSame(first, projectService.beginUploadAssessment(18L));
        verify(assessmentRepository(), times(1)).saveAndFlush(any());
        first.setStatus(com.riskwarning.common.enums.AssessmentStatusEnum.ASSESSING);
        assertThrows(BusinessException.class, () -> projectService.beginUploadAssessment(18L));
    }

    @Test void uploadFailureListenerPersistsOnceAndCannotOverwriteTerminalAssessment() {
        com.riskwarning.common.utils.KafkaUtils kafka = mock(com.riskwarning.common.utils.KafkaUtils.class);
        ReflectionTestUtils.setField(projectService, "kafka", kafka);
        com.riskwarning.common.po.report.Assessment row = com.riskwarning.common.po.report.Assessment.builder()
                .id(12L).projectId(18L).uploadTaskId("batch").status(com.riskwarning.common.enums.AssessmentStatusEnum.UPLOADING).build();
        when(em.find(com.riskwarning.common.po.report.Assessment.class, 12L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(row);
        com.riskwarning.common.message.AssessmentCompletedEventMessage event =
                new com.riskwarning.common.message.AssessmentCompletedEventMessage("failed", "now", "batch", 5L, 18L, 12L);
        event.setFailureStage("UPLOAD_CHUNK"); event.setFailureDetail("full exception stack");
        projectService.onUploadFailure(event);
        assertEquals(com.riskwarning.common.enums.AssessmentStatusEnum.FAILED, row.getStatus());
        assertEquals("UPLOAD_CHUNK_FAILED", row.getFailureCode()); assertNotNull(row.getFailedAt());
        projectService.onUploadFailure(event);
        verify(kafka, times(2)).sendMessage(any());
        row.setStatus(com.riskwarning.common.enums.AssessmentStatusEnum.ASSESSED);
        projectService.onUploadFailure(event);
        assertEquals(com.riskwarning.common.enums.AssessmentStatusEnum.ASSESSED, row.getStatus());
        verifyNoMoreInteractions(kafka);
    }

    @Test void confirmUsesTheSameAssessmentAndSubmitsDurably() {
        projectRole(ProjectRole.PROJECT_ADMIN);
        com.riskwarning.common.po.report.Assessment row = com.riskwarning.common.po.report.Assessment.builder()
                .id(12L).projectId(18L).uploadTaskId("batch").status(com.riskwarning.common.enums.AssessmentStatusEnum.UPLOADING).build();
        when(em.find(com.riskwarning.common.po.report.Assessment.class, 12L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(row);
        com.riskwarning.org.entity.dto.UploadConfirmDto task = com.riskwarning.org.entity.dto.UploadConfirmDto.builder()
                .projectId(18L).assessmentId(12L).taskId("batch").build();
        projectService.confirmUploadAssessment(task);
        projectService.confirmUploadAssessment(task);
        assertEquals(com.riskwarning.common.enums.AssessmentStatusEnum.TO_BE_ASSESSED, row.getStatus());
        verify(work, times(1)).enqueue(eq("UPLOAD_INBOX"), eq("batch"), contains("\"assessmentId\":12"));
    }

    private AssessmentRepository assessmentRepository() {
        return (AssessmentRepository) ReflectionTestUtils.getField(projectService, "assessmentRepository");
    }

    @Test void uploadAssessmentIsCreatedOnceBeforeMergingAndRejectsWrongProject() {
        AssessmentRepository repository = mock(AssessmentRepository.class);
        ProjectServiceImpl service = new ProjectServiceImpl(mock(ProjectRepository.class),
                mock(ProjectMemberRepository.class), em, repository);
        @SuppressWarnings("unchecked") TypedQuery<com.riskwarning.common.po.report.Assessment> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(com.riskwarning.common.po.report.Assessment.class))).thenReturn(query);
        when(query.setParameter("taskId", "upload")).thenReturn(query);
        when(query.getResultList()).thenReturn(java.util.Collections.emptyList());
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            com.riskwarning.common.po.report.Assessment assessment = call.getArgument(0);
            assessment.setId(12L);
            return assessment;
        });
        assertEquals(12L, service.prepareUploadAssessment(18L, "upload"));
        org.mockito.ArgumentCaptor<com.riskwarning.common.po.report.Assessment> created =
                org.mockito.ArgumentCaptor.forClass(com.riskwarning.common.po.report.Assessment.class);
        verify(repository).saveAndFlush(created.capture());
        assertEquals(com.riskwarning.common.enums.AssessmentStatusEnum.TO_BE_ASSESSED, created.getValue().getStatus());
        assertEquals("upload", created.getValue().getUploadTaskId());
        when(query.getResultList()).thenReturn(java.util.Collections.singletonList(created.getValue()));
        assertEquals(12L, service.prepareUploadAssessment(18L, "upload"));
        verify(repository, times(1)).saveAndFlush(any());
        created.getValue().setProjectId(99L);
        assertThrows(IllegalArgumentException.class, () -> service.prepareUploadAssessment(18L, "upload"));
    }
    private void enterpriseAdmin() {
        when(em.find(EnterpriseUser.class,new EnterpriseUserId(7L,5L)))
                .thenReturn(EnterpriseUser.builder().role(EnterpriseRole.admin).build());
    }
    private void projectRole(ProjectRole role) {
        when(em.find(ProjectMember.class,new ProjectMemberId(18L,5L)))
                .thenReturn(ProjectMember.builder().role(role).build());
    }

    @Test void partialEnterpriseUpdatePreservesIdentityAndUnspecifiedFields() {
        enterpriseAdmin(); EnterpriseUpdateRequest request = new EnterpriseUpdateRequest(); request.setName(" 企业B ");
        assertSame(enterprise,enterpriseService.updateEnterprise(7L,request));
        assertEquals("企业B",enterprise.getName()); assertEquals("original",enterprise.getCreditCode());
        assertEquals(7L,enterprise.getId()); assertEquals(LocalDateTime.of(2020,1,1,0,0),enterprise.getCreatedAt());
        assertNotNull(enterprise.getUpdatedAt());
    }
    @Test void duplicateCreditCodeFailsButOwnCodeIsAllowed() {
        enterpriseAdmin(); EnterpriseUpdateRequest request = new EnterpriseUpdateRequest(); request.setCreditCode("original");
        when(enterprises.findByCreditCode("original")).thenReturn(Optional.of(enterprise));
        enterpriseService.updateEnterprise(7L,request);
        request.setCreditCode("other");
        when(enterprises.findByCreditCode("other")).thenReturn(Optional.of(Enterprise.builder().id(8L).build()));
        assertThrows(BusinessException.class,()->enterpriseService.updateEnterprise(7L,request));
    }
    @Test void ordinaryMemberAndAnonymousUserCannotMutate() {
        when(em.find(EnterpriseUser.class,new EnterpriseUserId(7L,5L)))
                .thenReturn(EnterpriseUser.builder().role(EnterpriseRole.member).build());
        assertThrows(BusinessException.class,()->enterpriseService.deleteEnterprise(7L));
        assertThrows(BusinessException.class,()->enterpriseService.updateEnterprise(7L,new EnterpriseUpdateRequest()));
        projectRole(ProjectRole.EDITOR);
        assertThrows(BusinessException.class,()->projectService.updateProject(18L,new ProjectUpdateRequest()));
        assertThrows(BusinessException.class,()->projectService.deleteProject(18L));
        UserContext.clear(); assertThrows(BusinessException.class,()->projectService.deleteProject(18L));
        verify(em,never()).remove(any()); verifyNoInteractions(work);
    }
    @Test void validatesBlankNamesCapitalAndDates() {
        enterpriseAdmin(); EnterpriseUpdateRequest request = new EnterpriseUpdateRequest(); request.setName(" ");
        assertThrows(BusinessException.class,()->enterpriseService.updateEnterprise(7L,request));
        request.setName(null); request.setRegisteredCapital(BigDecimal.valueOf(-1));
        assertThrows(BusinessException.class,()->enterpriseService.updateEnterprise(7L,request));
        ProjectUpdateRequest projectRequest = new ProjectUpdateRequest(); projectRequest.setStartDate(LocalDate.of(2027,1,1));
        assertThrows(BusinessException.class,()->projectService.updateProject(18L,projectRequest));
    }
    @Test void projectAdminCanUpdateWithoutChangingEnterpriseOrAssessmentState() {
        projectRole(ProjectRole.PROJECT_ADMIN);
        ProjectUpdateRequest request = new ProjectUpdateRequest(); request.setName("项目B"); request.setType("SPECIAL");
        request.setDescription("");
        projectService.updateProject(18L,request);
        assertEquals("项目B",project.getName()); assertEquals(ProjectType.SPECIAL,project.getType());
        assertEquals(7L,project.getEnterpriseId()); assertEquals(ProjectStatus.COMPLETED,project.getStatus());
        assertEquals("",project.getDescription());
        request.setRegion("INVALID"); assertThrows(IllegalArgumentException.class,()->projectService.updateProject(18L,request));
    }
    @Test void activeAssessmentOrQueuedWorkBlocksDeletion() {
        enterpriseAdmin(); when(active.getSingleResult()).thenReturn(1L);
        assertThrows(BusinessException.class,()->projectService.deleteProject(18L));
        when(active.getSingleResult()).thenReturn(0L); when(pending.getSingleResult()).thenReturn(1L);
        assertThrows(BusinessException.class,()->projectService.deleteProject(18L));
        verify(em,never()).remove(any()); verifyNoInteractions(work);
    }
    @Test void projectDeletionClearsDependentsAndSchedulesScopedCleanup() {
        enterpriseAdmin(); projectService.deleteProject(18L);
        verify(em).createQuery("delete from IndicatorResult r where r.projectId = :id");
        verify(em).createQuery("delete from Assessment a where a.projectId = :id");
        verify(em).createQuery("delete from ProjectFile f where f.projectId = :id");
        verify(em).createQuery("delete from ProjectMember m where m.id.projectId = :id");
        verify(em).remove(project);
        verify(work).enqueue(eq("PROJECT_RESOURCE_CLEANUP"),eq("delete-project-18"),contains("18"));
    }
    @Test void enterpriseDeletionValidatesAllChildrenBeforeRemovingAny() {
        enterpriseAdmin(); Project second = Project.builder().id(19L).enterpriseId(7L).build();
        TypedQuery<Project> children = mock(TypedQuery.class);
        when(em.createQuery(anyString(),eq(Project.class))).thenReturn(children);
        when(children.setParameter(anyString(),any())).thenReturn(children);
        when(children.setLockMode(any())).thenReturn(children);
        when(children.getResultList()).thenReturn(Arrays.asList(project,second));
        when(active.getSingleResult()).thenReturn(0L,1L);
        assertThrows(BusinessException.class,()->enterpriseService.deleteEnterprise(7L));
        verify(em,never()).remove(any()); verifyNoInteractions(work);
        when(active.getSingleResult()).thenReturn(0L);
        enterpriseService.deleteEnterprise(7L);
        verify(em).remove(project); verify(em).remove(second); verify(em).remove(enterprise);
        verify(work,times(2)).enqueue(eq("PROJECT_RESOURCE_CLEANUP"),anyString(),anyString());
    }
    @Test void missingOrInvalidIdsReturnBusinessErrors() {
        assertThrows(BusinessException.class,()->projectService.deleteProject(-1L));
        assertThrows(BusinessException.class,()->enterpriseService.deleteEnterprise(999L));
        assertThrows(BusinessException.class,()->enterpriseService.getEnterprise(null));
    }
}
