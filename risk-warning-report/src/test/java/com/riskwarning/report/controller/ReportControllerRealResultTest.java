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
}
