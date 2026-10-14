package com.riskwarning.report.controller;

import com.riskwarning.common.hardcode.FixedAssessmentData;
import com.riskwarning.report.entity.vo.general.AssessmentDetailVO;
import com.riskwarning.report.entity.vo.indicator.IndicatorDistributionVO;
import com.riskwarning.report.entity.vo.risk.RiskVO;
import com.riskwarning.report.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验 report 的三个评估结果接口与请求参数完全无关：
 * 无论传什么 id（甚至不传），都返回同一份写死的评估结果。
 */
class ReportControllerFixedResultTest {

    private final ReportController controller = new ReportController();

    @BeforeEach
    void injectService() throws Exception {
        // 写死分支不访问数据库与 ES，service 内部依赖留空即可
        Field field = ReportController.class.getDeclaredField("reportService");
        field.setAccessible(true);
        field.set(controller, new ReportServiceImpl());
    }

    @Test
    void generalAlwaysReturnsFixedAssessment() {
        AssessmentDetailVO byArbitraryId = (AssessmentDetailVO) controller.reportGeneral(123L).getData();
        AssessmentDetailVO byAnotherId = (AssessmentDetailVO) controller.reportGeneral(999999L).getData();
        AssessmentDetailVO withoutId = (AssessmentDetailVO) controller.reportGeneral(null).getData();

        for (AssessmentDetailVO vo : new AssessmentDetailVO[]{byArbitraryId, byAnotherId, withoutId}) {
            assertNotNull(vo);
            assertEquals(FixedAssessmentData.ASSESSMENT_ID, vo.getAssessmentId());
            assertEquals(FixedAssessmentData.PROJECT_ID, vo.getProjectId());
            assertEquals(23.58, vo.getOverallResult().getOverallScore());
            assertEquals(12, vo.getRiskSummary().getTotalRisks());
            assertEquals(12, vo.getIndicatorOverview().getBehaviorIndicators());
        }
    }

    @Test
    void indicatorResultAlwaysReturnsFixedAssessment() {
        IndicatorDistributionVO byArbitraryId =
                (IndicatorDistributionVO) controller.reportIndicatorResult(123L).getData();
        IndicatorDistributionVO byAnotherId =
                (IndicatorDistributionVO) controller.reportIndicatorResult(999999L).getData();
        IndicatorDistributionVO withoutId =
                (IndicatorDistributionVO) controller.reportIndicatorResult(null).getData();

        for (IndicatorDistributionVO vo : new IndicatorDistributionVO[]{byArbitraryId, byAnotherId, withoutId}) {
            assertNotNull(vo);
            assertEquals(FixedAssessmentData.ASSESSMENT_ID, vo.getAssessmentId());
            assertEquals(23.58, vo.getTotalScore());
            assertEquals(12, vo.getTotalCount());
            assertEquals(12, vo.getIndicatorScores().size());
        }
    }

    @Test
    void riskAlwaysReturnsFixedAssessment() {
        List<RiskVO> byArbitraryId = (List<RiskVO>) controller.reportRisk(123L).getData();
        List<RiskVO> byAnotherId = (List<RiskVO>) controller.reportRisk(999999L).getData();
        List<RiskVO> withoutId = (List<RiskVO>) controller.reportRisk(null).getData();

        for (List<RiskVO> risks : List.of(byArbitraryId, byAnotherId, withoutId)) {
            assertNotNull(risks);
            assertEquals(12, risks.size());
            assertEquals("企业关联方风险", risks.get(0).getDimension());
            assertEquals(30.25, risks.get(0).getScore());
            assertNotNull(risks.get(0).getRelatedIndicator());
            assertTrue(risks.get(0).getRelatedIndicator()
                    .startsWith("企业关联方风险 > 合作伙伴（合资方、战略合作方） > 合作方违规 > "));
        }
    }

    /**
     * 三个接口不得再依赖数据库取数：一旦有人把「按 id 回查评估记录」加回控制器，
     * 传入真实评估记录 id 就会返回真实数据（而非模拟数据），与本约定冲突。
     */
    @Test
    void controllerDoesNotLookUpAnyStoredAssessment() {
        assertThrows(NoSuchFieldException.class,
                () -> ReportController.class.getDeclaredField("assessmentRepository"));
    }
}
