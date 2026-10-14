package com.riskwarning.org.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.riskwarning.common.enums.risk.RiskLevelEnum;
import com.riskwarning.common.hardcode.FixedAssessmentData;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.org.service.impl.EnterpriseServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验 org 的 {@code /enterprise/{name}/assessment} 接口：任意企业名都返回同一份写死的评估结果，
 * 且 details 里带着与 report 一致的 general + 指标分布详情。
 *
 * <p>写死分支不访问数据库，这里的 repository / EntityManager 依赖传 null 即可。</p>
 */
class EnterpriseAssessmentFixedResultTest {

    private final EnterpriseServiceImpl service =
            new EnterpriseServiceImpl(null, null, null, null, null);

    @Test
    void anyEnterpriseNameReturnsSameFixedAssessment() {
        Assessment byRealName = service.getLatestAssessmentByEnterpriseName(FixedAssessmentData.ENTERPRISE_NAME);
        Assessment byUnknownName = service.getLatestAssessmentByEnterpriseName("随便一个不存在的企业名称");
        Assessment byNullName = service.getLatestAssessmentByEnterpriseName(null);

        for (Assessment assessment : new Assessment[]{byRealName, byUnknownName, byNullName}) {
            assertNotNull(assessment);
            assertEquals(FixedAssessmentData.ASSESSMENT_ID, assessment.getId());
            assertEquals(FixedAssessmentData.PROJECT_ID, assessment.getProjectId());
            assertEquals(23.58, assessment.getOverallScore());
            assertEquals(RiskLevelEnum.MEDIUM_RISK, assessment.getOverallRiskLevel());
        }
    }

    @Test
    void detailsCarryGeneralAndIndicatorSnapshot() {
        Assessment assessment = service.getLatestAssessmentByEnterpriseName("任意企业名称");
        String details = assessment.getDetails();
        assertNotNull(details, "org 返回的评估结果必须带上 details 详情");

        JSONObject root = JSON.parseObject(details);
        JSONObject general = root.getJSONObject("assessmentDetailVO");
        JSONObject indicator = root.getJSONObject("indicatorDistributionVO");

        assertNotNull(general);
        assertNotNull(indicator);
        assertEquals(FixedAssessmentData.ASSESSMENT_ID, general.getLong("assessmentId"));
        assertEquals(23.58, general.getJSONObject("overallResult").getDoubleValue("overallScore"));
        assertEquals(12, general.getJSONObject("riskSummary").getIntValue("totalRisks"));
        assertEquals(FixedAssessmentData.ASSESSMENT_ID, indicator.getLong("assessmentId"));
        assertEquals(23.58, indicator.getDoubleValue("totalScore"));
        assertEquals(12, indicator.getIntValue("totalCount"));

        // details 里每条指标都带知识库完整指标路径，前端在 org 这一侧也能直接拿到
        JSONArray indicatorScores = indicator.getJSONArray("indicatorScores");
        assertEquals(12, indicatorScores.size());
        for (int i = 0; i < indicatorScores.size(); i++) {
            JSONObject score = indicatorScores.getJSONObject(i);
            String path = score.getString("relatedIndicator");
            assertNotNull(path, "details 中的指标缺少完整指标路径");
            assertTrue(path.startsWith(score.getString("dimension") + " > "));
            assertTrue(path.endsWith(" > " + score.getString("indicatorName")));
        }
        assertEquals("企业关联方风险 > 合作伙伴（合资方、战略合作方） > 合作方违规 > 企业合作伙伴是否存在违法违规行为",
                indicatorScores.getJSONObject(0).getString("relatedIndicator"));
    }
}
