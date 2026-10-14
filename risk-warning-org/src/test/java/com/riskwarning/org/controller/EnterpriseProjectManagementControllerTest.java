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
