package com.riskwarning.org.controller;

import com.riskwarning.common.annotation.AuthRequired;
import com.riskwarning.common.dto.enterprise.EnterpriseUpdateRequest;
import com.riskwarning.common.dto.project.ProjectUpdateRequest;
import com.riskwarning.common.po.enterprise.Enterprise;
import com.riskwarning.common.po.project.Project;
import com.riskwarning.org.service.EnterpriseService;
import com.riskwarning.org.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EnterpriseProjectManagementControllerTest {
    @Test void projectWithNoAssessmentReturnsSuccessfulNullData() throws Exception {
        when(projectService.getAssessmentByProjectId(358L)).thenReturn(null);
        mvc.perform(get("/project/358/assessment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }
    @Test void enterpriseLatestReportKeepsPublicRouteAndNestedFailureInformation() throws Exception {
        java.util.Map<String, Object> overall = new java.util.LinkedHashMap<>();
        overall.put("status", "FAILED");
        overall.put("failureCode", "INDICATOR_CALCULATION_FAILED");
        overall.put("failureReason", "指标计算失败，请稍后重新评估");
        overall.put("failureStage", "INDICATOR_CALCULATION");
        overall.put("failedAt", "2026-10-15T14:30:00");
        java.util.Map<String, Object> report = new java.util.LinkedHashMap<>();
        report.put("projectId", 20L);
        report.put("assessmentId", 81L);
        report.put("overallResult", overall);
        when(enterpriseService.getLatestAssessmentReportByEnterpriseName("企业A")).thenReturn(report);
        mvc.perform(get("/enterprise/{name}/assessment", "企业A").characterEncoding("UTF-8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.assessmentId").value(81))
                .andExpect(jsonPath("$.data.overallResult.status").value("FAILED"))
                .andExpect(jsonPath("$.data.overallResult.failureReason").value("指标计算失败，请稍后重新评估"))
                .andExpect(jsonPath("$.data.overallResult.failureStage").value("INDICATOR_CALCULATION"))
                .andExpect(jsonPath("$.data.overallResult.failedAt").value("2026-10-15T14:30:00"))
                .andExpect(jsonPath("$.data.details").doesNotExist())
                .andExpect(jsonPath("$.data.data").doesNotExist());
        verify(enterpriseService).getLatestAssessmentReportByEnterpriseName("企业A");
    }
    private final EnterpriseService enterpriseService = mock(EnterpriseService.class);
    private final ProjectService projectService = mock(ProjectService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new EnterpriseController(enterpriseService),new ProjectController(projectService)).build();
    @Test void updateRoutesPassPathIdAndEditableBody() throws Exception {
        when(enterpriseService.updateEnterprise(eq(7L),any())).thenReturn(Enterprise.builder().id(7L).name("企业B").build());
        mvc.perform(put("/enterprise/7").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"企业B\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(7));
        ArgumentCaptor<EnterpriseUpdateRequest> enterprise = ArgumentCaptor.forClass(EnterpriseUpdateRequest.class);
        verify(enterpriseService).updateEnterprise(eq(7L),enterprise.capture()); assertEquals("企业B",enterprise.getValue().getName());
        when(projectService.updateProject(eq(18L),any())).thenReturn(Project.builder().id(18L).name("项目B").build());
        mvc.perform(patch("/project/18").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"项目B\",\"type\":\"SPECIAL\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(18));
        ArgumentCaptor<ProjectUpdateRequest> project = ArgumentCaptor.forClass(ProjectUpdateRequest.class);
        verify(projectService).updateProject(eq(18L),project.capture()); assertEquals("SPECIAL",project.getValue().getType());
    }
    @Test void deleteAndDetailsRoutesUseRequestedIds() throws Exception {
        mvc.perform(delete("/enterprise/7")).andExpect(status().isOk()); verify(enterpriseService).deleteEnterprise(7L);
        mvc.perform(delete("/project/18")).andExpect(status().isOk()); verify(projectService).deleteProject(18L);
        mvc.perform(get("/enterprise/7")).andExpect(status().isOk()); verify(enterpriseService).getEnterprise(7L);
        mvc.perform(get("/project/18")).andExpect(status().isOk()); verify(projectService).getProject(18L);
    }
    @Test void everyManagementEndpointRequiresAuthentication() {
        for (Class<?> controller : new Class<?>[]{EnterpriseController.class,ProjectController.class}) {
            for (java.lang.reflect.Method method : controller.getDeclaredMethods()) {
                if (method.getName().startsWith("update") || method.getName().startsWith("delete")
                        || method.getName().equals("enterprise") || method.getName().equals("project")) {
                    assertTrue(method.isAnnotationPresent(AuthRequired.class),method.getName());
                }
            }
        }
    }
}
