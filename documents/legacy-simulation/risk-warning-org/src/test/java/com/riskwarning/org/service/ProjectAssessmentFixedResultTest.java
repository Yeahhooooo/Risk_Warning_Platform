package com.riskwarning.org.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.riskwarning.common.enums.risk.RiskLevelEnum;
import com.riskwarning.common.hardcode.FixedAssessmentData;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.org.service.impl.ProjectServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 校验 org 的 {@code /project/{projectId}/assessment} 接口：任意 projectId 都返回同一份写死的评估结果。
 *
 * <p>写死分支不访问数据库，这里的 repository / EntityManager 依赖传 null 即可——
 * 一旦有人把「按项目 ID 查库」加回来，本测试会因为空指针立刻变红。</p>
 */
class ProjectAssessmentFixedResultTest {

    private final ProjectServiceImpl service = new ProjectServiceImpl(null, null, null);

    @Test
    void anyProjectIdReturnsSameFixedAssessment() {
        Assessment byRealProject = service.getAssessmentByProjectId(349L);
        Assessment byUnknownProject = service.getAssessmentByProjectId(999999L);
        Assessment byNullProject = service.getAssessmentByProjectId(null);

        for (Assessment assessment : new Assessment[]{byRealProject, byUnknownProject, byNullProject}) {
            assertNotNull(assessment);
            assertEquals(FixedAssessmentData.ASSESSMENT_ID, assessment.getId());
            assertEquals(FixedAssessmentData.PROJECT_ID, assessment.getProjectId());
            assertEquals(23.58, assessment.getOverallScore());
            assertEquals(RiskLevelEnum.MEDIUM_RISK, assessment.getOverallRiskLevel());
        }
    }

    @Test
    void detailsCarryFixedGeneralAndIndicators() {
        Assessment assessment = service.getAssessmentByProjectId(12345L);
        String details = assessment.getDetails();
        assertNotNull(details, "org 返回的评估结果必须带上 details 详情");

        JSONObject root = JSON.parseObject(details);
        JSONObject general = root.getJSONObject("assessmentDetailVO");
        JSONObject indicator = root.getJSONObject("indicatorDistributionVO");

        assertEquals(FixedAssessmentData.ASSESSMENT_ID, general.getLong("assessmentId"));
        assertEquals(23.58, general.getJSONObject("overallResult").getDoubleValue("overallScore"));
        assertEquals(12, general.getJSONObject("riskSummary").getIntValue("totalRisks"));
        assertEquals(12, indicator.getIntValue("totalCount"));

        JSONArray indicatorScores = indicator.getJSONArray("indicatorScores");
        assertEquals(12, indicatorScores.size());
        for (int i = 0; i < indicatorScores.size(); i++) {
            assertNotNull(indicatorScores.getJSONObject(i).getString("relatedIndicator"));
        }
    }
}
