package com.riskwarning.org.service;

import com.riskwarning.common.po.enterprise.Enterprise;
import com.riskwarning.common.po.project.Project;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.org.repository.*;
import com.riskwarning.org.service.impl.*;
import org.junit.jupiter.api.Test;
import javax.persistence.EntityManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.common.result.Result;
import com.riskwarning.common.exception.BusinessException;
import org.springframework.test.util.ReflectionTestUtils;

class RealAssessmentLookupTest {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final AssessmentRepository assessments = mock(AssessmentRepository.class);
    private final EnterpriseRepository enterprises = mock(EnterpriseRepository.class);
    private final ProjectServiceImpl projectService = new ProjectServiceImpl(projects,
            mock(ProjectMemberRepository.class), mock(EntityManager.class), assessments);
    private final EnterpriseServiceImpl enterpriseService = new EnterpriseServiceImpl(enterprises,
            mock(EnterpriseUserRepository.class), projects, assessments, mock(EntityManager.class));
    private final EnterpriseService.ReportClient reports = mock(EnterpriseService.ReportClient.class);

    RealAssessmentLookupTest() {
        ReflectionTestUtils.setField(enterpriseService, "reportClient", reports);
    }

    @Test void projectReturnsLatestStoredAssessment() {
        Project project = new Project(); project.setId(18L);
        Assessment latest = Assessment.builder().id(72L).projectId(18L).build();
        when(projects.findById(18L)).thenReturn(Optional.of(project));
        when(assessments.findFirstByProjectIdOrderByIdDesc(18L)).thenReturn(Optional.of(latest));
        assertSame(latest, projectService.getAssessmentByProjectId(18L));
        assertThrows(IllegalArgumentException.class, () -> projectService.getAssessmentByProjectId(19L));
    }

    @Test void firstUploadCanReadAnEmptyAssessmentBaseline() {
        when(projects.findById(358L)).thenReturn(Optional.of(Project.builder().id(358L).build()));
        when(assessments.findFirstByProjectIdOrderByIdDesc(358L)).thenReturn(Optional.empty());
        assertNull(projectService.getAssessmentByProjectId(358L));
        assertNull(projectService.getAssessmentByProjectId(358L), "Polling before handoff must also return null");
        Assessment created = Assessment.builder().id(81L).projectId(358L).status(AssessmentStatusEnum.TO_BE_ASSESSED).build();
        when(assessments.findFirstByProjectIdOrderByIdDesc(358L)).thenReturn(Optional.of(created));
        assertSame(created, projectService.getAssessmentByProjectId(358L));
        verify(assessments, never()).save(any());
    }

    @Test void missingProjectIsStillAnErrorAndDoesNotQueryAssessment() {
        when(projects.findById(999L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> projectService.getAssessmentByProjectId(999L));
        verifyNoInteractions(assessments);
    }
    @Test void enterpriseLookupIsScopedToItsProjects() {
        Enterprise enterprise = new Enterprise(); enterprise.setId(7L);
        Project first = new Project(); first.setId(18L);
        Project second = new Project(); second.setId(20L);
        Assessment latest = Assessment.builder().id(81L).projectId(20L).build();
        when(enterprises.findByName("企业A")).thenReturn(Optional.of(enterprise));
        when(projects.findByEnterpriseId(7L)).thenReturn(Arrays.asList(first, second));
        when(assessments.findFirstByProjectIdInOrderByIdDesc(Arrays.asList(18L,20L)))
                .thenReturn(Optional.of(latest));
        assertSame(latest, enterpriseService.getLatestAssessmentByEnterpriseName("企业A"));
        assertThrows(IllegalArgumentException.class, () -> enterpriseService.getLatestAssessmentByEnterpriseName("不存在"));
    }
    @Test void enterpriseWithoutProjectsHasNoFabricatedResult() {
        Enterprise enterprise = new Enterprise(); enterprise.setId(7L);
        when(enterprises.findByName("企业A")).thenReturn(Optional.of(enterprise));
        when(projects.findByEnterpriseId(7L)).thenReturn(Collections.emptyList());
        assertNull(enterpriseService.getLatestAssessmentByEnterpriseName("企业A"));
        verifyNoInteractions(assessments);
    }

    private Assessment latest(AssessmentStatusEnum status) {
        Enterprise enterprise = new Enterprise(); enterprise.setId(7L);
        Project project = new Project(); project.setId(20L);
        Assessment latest = Assessment.builder().id(81L).projectId(20L).status(status).build();
        when(enterprises.findByName("企业A")).thenReturn(Optional.of(enterprise));
        when(projects.findByEnterpriseId(7L)).thenReturn(Collections.singletonList(project));
        when(assessments.findFirstByProjectIdInOrderByIdDesc(Collections.singletonList(20L))).thenReturn(Optional.of(latest));
        return latest;
    }

    @Test void enterpriseReturnsReportPayloadIncludingLatestFailureWithoutFlatteningOrParsingSnapshots() {
        for (AssessmentStatusEnum status : AssessmentStatusEnum.values()) {
            Assessment latest = latest(status);
            Map<String, Object> overall = new LinkedHashMap<>();
            overall.put("status", status.name());
            overall.put("failureReason", status == AssessmentStatusEnum.FAILED ? "指标计算失败，请稍后重新评估" : null);
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("assessmentId", latest.getId());
            report.put("projectId", latest.getProjectId());
            report.put("overallResult", overall);
            when(reports.general(81L)).thenReturn(Result.success(report));
            assertSame(report, enterpriseService.getLatestAssessmentReportByEnterpriseName("企业A"));
        }
        verify(reports, times(AssessmentStatusEnum.values().length)).general(81L);
        verify(assessments, never()).save(any());
    }

    @Test void absentAssessmentDoesNotCallReportService() {
        latest(AssessmentStatusEnum.ASSESSED);
        when(assessments.findFirstByProjectIdInOrderByIdDesc(Collections.singletonList(20L))).thenReturn(Optional.empty());
        assertNull(enterpriseService.getLatestAssessmentReportByEnterpriseName("企业A"));
        verifyNoInteractions(reports);
    }

    @Test void queryFailureDoesNotBecomeAnAssessmentFailureOrExposeInternalEndpoint() {
        Assessment row = latest(AssessmentStatusEnum.ASSESSED);
        when(reports.general(81L)).thenThrow(mock(feign.FeignException.class));
        BusinessException error = assertThrows(BusinessException.class,
                () -> enterpriseService.getLatestAssessmentReportByEnterpriseName("企业A"));
        assertEquals(503, error.getCode());
        assertTrue(error.getMessage().contains("81"));
        assertEquals(AssessmentStatusEnum.ASSESSED, row.getStatus());
        verify(assessments, never()).save(any());
    }

    @Test void invalidReportResponseAndReportBusinessErrorsAreNotReturnedAsSuccess() {
        latest(AssessmentStatusEnum.FAILED);
        when(reports.general(81L)).thenReturn(null);
        assertEquals(502, assertThrows(BusinessException.class,
                () -> enterpriseService.getLatestAssessmentReportByEnterpriseName("企业A")).getCode());
        when(reports.general(81L)).thenReturn(Result.success());
        assertEquals(502, assertThrows(BusinessException.class,
                () -> enterpriseService.getLatestAssessmentReportByEnterpriseName("企业A")).getCode());
        when(reports.general(81L)).thenReturn(Result.fail(500, "评估详情损坏，请重新评估"));
        assertEquals("评估详情损坏，请重新评估", assertThrows(BusinessException.class,
                () -> enterpriseService.getLatestAssessmentReportByEnterpriseName("企业A")).getMessage());
    }
}
