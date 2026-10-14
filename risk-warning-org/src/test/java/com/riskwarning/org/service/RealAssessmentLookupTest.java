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

class RealAssessmentLookupTest {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final AssessmentRepository assessments = mock(AssessmentRepository.class);
    private final EnterpriseRepository enterprises = mock(EnterpriseRepository.class);
    private final ProjectServiceImpl projectService = new ProjectServiceImpl(projects,
            mock(ProjectMemberRepository.class), mock(EntityManager.class), assessments);
    private final EnterpriseServiceImpl enterpriseService = new EnterpriseServiceImpl(enterprises,
            mock(EnterpriseUserRepository.class), projects, assessments, mock(EntityManager.class));

    @Test void projectReturnsLatestStoredAssessment() {
        Project project = new Project(); project.setId(18L);
        Assessment latest = Assessment.builder().id(72L).projectId(18L).build();
        when(projects.findById(18L)).thenReturn(Optional.of(project));
        when(assessments.findFirstByProjectIdOrderByIdDesc(18L)).thenReturn(Optional.of(latest));
        assertSame(latest, projectService.getAssessmentByProjectId(18L));
        assertThrows(IllegalArgumentException.class, () -> projectService.getAssessmentByProjectId(19L));
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
}
