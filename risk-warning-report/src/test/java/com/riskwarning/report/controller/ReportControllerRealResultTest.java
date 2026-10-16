package com.riskwarning.report.controller;

import com.riskwarning.common.exception.BusinessException;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.report.repository.AssessmentRepository;
import com.riskwarning.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportControllerRealResultTest {
    private final AssessmentRepository repository = mock(AssessmentRepository.class);
    private final ReportService service = mock(ReportService.class);
    private final ReportController controller = new ReportController();
    ReportControllerRealResultTest() {
        ReflectionTestUtils.setField(controller, "assessmentRepository", repository);
        ReflectionTestUtils.setField(controller, "reportService", service);
    }
    @Test void allEndpointsUseRequestedAssessment() {
        for (long id : new long[]{12L, 34L, 900001L}) {
            Assessment row = Assessment.builder().id(id).projectId(id + 1).build();
            when(repository.findById(id)).thenReturn(Optional.of(row));
            controller.reportGeneral(id);
            controller.reportIndicatorResult(id);
            controller.reportRisk(id);
            verify(service).assembleGeneral(row);
            verify(service).assembleIndicatorResult(row);
            verify(service).assembleRisk(id);
        }
    }
    @Test void absentAssessmentNeverFallsBackToDemo() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> controller.reportGeneral(99L));
        assertThrows(BusinessException.class, () -> controller.reportIndicatorResult(99L));
        assertThrows(BusinessException.class, () -> controller.reportRisk(99L));
        verifyNoInteractions(service);
    }
    @Test void storedSimulationIsRejectedByEveryEndpoint() {
        Assessment row = Assessment.builder().id(42L).projectId(9L).details(
                "{\"assessmentDetailVO\": {\"projectId\":900001, \"assessmentId\":900001}}").build();
        when(repository.findById(42L)).thenReturn(Optional.of(row));
        assertThrows(BusinessException.class, () -> controller.reportGeneral(42L));
        assertThrows(BusinessException.class, () -> controller.reportIndicatorResult(42L));
        assertThrows(BusinessException.class, () -> controller.reportRisk(42L));
        verifyNoInteractions(service);
    }

    @Test void statusKeepsExistingResponseAndRouteWithoutQueryingReport() throws Exception {
        com.riskwarning.report.service.AssessmentService assessments = mock(com.riskwarning.report.service.AssessmentService.class);
        ReflectionTestUtils.setField(controller, "assessmentService", assessments);
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("assessmentId", 12L);
        data.put("status", "FAILED");
        data.put("failureReason", "评估报告汇总失败，请稍后重新评估");
        when(assessments.getAssessmentStatus(12L)).thenReturn(data);
        assertSame(data, controller.status(12L).getData());
        verifyNoInteractions(service, repository);
        org.springframework.web.bind.annotation.GetMapping mapping = ReportController.class
                .getMethod("status", Long.class).getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
        assertArrayEquals(new String[]{"/assessment/{assessmentId}/status"}, mapping.value());
        assertArrayEquals(new String[]{"/report/report/general/{assessmentId}"}, ReportController.class
                .getMethod("reportGeneral", Long.class).getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value());
    }
}
