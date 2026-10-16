package com.riskwarning.processing.service;

import com.riskwarning.common.message.AssessmentCompletedEventMessage;
import com.riskwarning.common.utils.KafkaUtils;
import com.riskwarning.processing.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssessmentCompletionBoundaryTest {
    @Test void indicatorCompletionOnlySchedulesReportAndDoesNotMarkAssessmentCompleted() {
        BehaviorProcessingService service = new BehaviorProcessingService();
        IndicatorResultRepository indicators = mock(IndicatorResultRepository.class);
        AssessmentRepository assessments = mock(AssessmentRepository.class);
        KafkaUtils kafka = mock(KafkaUtils.class);
        ReflectionTestUtils.setField(service, "indicatorResultRepository", indicators);
        ReflectionTestUtils.setField(service, "assessmentRepository", assessments);
        ReflectionTestUtils.setField(service, "kafkaUtils", kafka);
        when(indicators.countByAssessmentId(12L)).thenReturn(1L);
        ReflectionTestUtils.invokeMethod(service, "completeAssessmentIfNeeded", 8L, 10L, 12L);
        verify(kafka).sendMessage(any(AssessmentCompletedEventMessage.class));
        verifyNoInteractions(assessments);
        doThrow(new IllegalStateException("outbox failed")).when(kafka).sendMessage(any());
        assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "completeAssessmentIfNeeded", 8L, 10L, 12L));
        verifyNoInteractions(assessments);
    }
}
