package com.riskwarning.common.utils;

import com.riskwarning.common.exception.BusinessException;
import com.riskwarning.common.po.report.Assessment;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssessmentDataGuardTest {
    @Test void snapshotMarkerSurvivesJsonFormatting() {
        Assessment row = Assessment.builder().id(1L).projectId(2L).details(
                "{\"assessmentDetailVO\":{\"projectId\":900001,\"assessmentId\":900001}}").build();
        assertThrows(BusinessException.class, () -> AssessmentDataGuard.requireReal(row));
    }
    @Test void realRecordAtFormerDemoIdStillUsesRealData() {
        Assessment row = Assessment.builder().id(900001L).projectId(2L).details(
                "{\"assessmentDetailVO\":{\"projectId\":2,\"assessmentId\":900001}}").build();
        assertSame(row, AssessmentDataGuard.requireReal(row));
    }
    @Test void absentOrCorruptRecordCannotProduceSuccess() {
        assertThrows(BusinessException.class, () -> AssessmentDataGuard.requireReal(null));
        assertThrows(BusinessException.class, () -> AssessmentDataGuard.requireReal(
                Assessment.builder().details("invalid json").build()));
    }
}
