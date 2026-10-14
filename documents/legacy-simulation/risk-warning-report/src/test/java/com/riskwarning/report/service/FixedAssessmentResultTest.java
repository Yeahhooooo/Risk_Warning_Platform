package com.riskwarning.report.service;

import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.enums.RiskDimensionEnum;
import com.riskwarning.common.enums.risk.RiskLevelEnum;
import com.riskwarning.common.hardcode.FixedAssessmentData;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.report.entity.vo.AssessmentGeneralDetails;
import com.riskwarning.report.entity.vo.general.AssessmentDetailVO;
import com.riskwarning.report.entity.vo.indicator.IndicatorDistributionVO;
import com.riskwarning.report.entity.vo.indicator.IndicatorScoreVO;
import com.riskwarning.report.entity.vo.risk.RiskVO;
import com.riskwarning.report.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验写死的评估结果在 report 侧仍符合既有返回格式与得分口径。
 * 写死数据不依赖数据库与 ES，这里直接构造 service 即可。
 */
class FixedAssessmentResultTest {

    private final ReportServiceImpl service = new ReportServiceImpl();
    private final Assessment assessment = FixedAssessmentData.assessment();

    @Test
    void fixedAssessmentKeepsOverallContract() {
        assertEquals(FixedAssessmentData.ASSESSMENT_ID, assessment.getId());
        assertEquals(23.58, assessment.getOverallScore());
        assertEquals(RiskLevelEnum.MEDIUM_RISK, assessment.getOverallRiskLevel());
        assertTrue(FixedAssessmentData.matches(assessment.getId()));
    }

    @Test
    void indicatorResultKeepsTwelveThirdLevelIndicators() {
        IndicatorDistributionVO result = service.assembleIndicatorResult(assessment);

        assertEquals(12, result.getIndicatorScores().size());
        assertEquals(12, result.getTotalCount());
        assertEquals(12, result.getRiskTriggeredCount());
        assertEquals(0, result.getSafeCount());
        assertEquals(23.58, result.getTotalScore());
        assertEquals(100.0, result.getMaxScore());
        assertEquals(6, result.getDimensionDistributions().size());

        for (IndicatorScoreVO indicator : result.getIndicatorScores()) {
            assertNotNull(indicator.getIndicatorName());
            assertEquals(100.0, indicator.getMaxScore());
            assertTrue(indicator.getRiskTriggered());
            assertNotNull(indicator.getRiskLevel());
            assertFalse(indicator.getRelatedBehaviors().isEmpty());
            assertNotNull(indicator.getRelatedIndicator());
        }

        // 4 个分数区间合计仍为全部指标
        assertEquals(12, result.getScoreDistributions().stream()
                .mapToInt(bucket -> bucket.getTotalCount()).sum());
    }

    @Test
    void generalKeepsSummaryAndDimensionDistribution() {
        AssessmentDetailVO general = service.assembleGeneral(assessment);

        assertEquals(23.58, general.getOverallResult().getOverallScore());
        assertEquals(RiskLevelEnum.MEDIUM_RISK, general.getOverallResult().getOverallRiskLevel());
        assertEquals(12, general.getRiskSummary().getTotalRisks());
        assertEquals(4, general.getRiskSummary().getHighRiskCount());
        assertEquals(8, general.getRiskSummary().getMediumRiskCount());
        // 沿用既有实现：没有低风险项时 lowRiskCount 保持 null，不额外补 0
        assertNull(general.getRiskSummary().getLowRiskCount());
        assertEquals(6, general.getDimensionRiskDistribution().size());
        assertEquals(12, general.getIndicatorOverview().getBehaviorIndicators());
        assertEquals(12, general.getIndicatorOverview().getRiskTriggeredIndicators());
        assertEquals(0, general.getIndicatorOverview().getSafeIndicators());

        assertEquals(1, general.getDimensionRiskDistribution()
                .get(RiskDimensionEnum.ENTERPRISE_RELATED_RISK).getRiskCount());
        assertEquals(2, general.getDimensionRiskDistribution()
                .get(RiskDimensionEnum.PRODUCT_LEGITIMACY_RISK).getRiskCount());
        assertEquals(2, general.getDimensionRiskDistribution()
                .get(RiskDimensionEnum.LABOR_LEGITIMACY_RISK).getRiskCount());
        assertEquals(2, general.getDimensionRiskDistribution()
                .get(RiskDimensionEnum.ENTERPRISE_CREDIT_RISK).getRiskCount());
        assertEquals(3, general.getDimensionRiskDistribution()
                .get(RiskDimensionEnum.ENTERPRISE_INTERNATIONAL_COOPERATION_RISK).getRiskCount());
        assertEquals(2, general.getDimensionRiskDistribution()
                .get(RiskDimensionEnum.SUPPLY_CHAIN_RISK).getRiskCount());
    }

    @Test
    void riskListKeepsIndicatorEvidence() {
        List<RiskVO> risks = service.assembleRisk(FixedAssessmentData.ASSESSMENT_ID);

        assertEquals(12, risks.size());
        RiskVO first = risks.get(0);
        assertEquals("企业关联方风险", first.getDimension());
        assertEquals("企业合作伙伴是否存在违法违规行为", first.getName());
        assertEquals("MEDIUM_RISK", first.getRiskLevel());
        assertEquals(30.25, first.getRelatedIndicators().get(0).getScore());
        assertEquals(30.25, first.getScore());
        assertEquals(100.0, first.getMaxScore());
        assertEquals("成功", first.getProcessingStatus());
        assertEquals("企业关联方风险 > 合作伙伴（合资方、战略合作方） > 合作方违规 > 企业合作伙伴是否存在违法违规行为",
                first.getRelatedIndicator());
    }

    /**
     * 每个风险点 / 每条指标都要带上知识库里的完整指标路径
     * （维度 &gt; 二级指标 &gt; 三级指标 &gt; 三级指标项），且与固定数据集里的 12 条路径一一对应。
     */
    @Test
    void relatedIndicatorExposesFullKnowledgeBasePath() {
        Map<String, String> expected = FixedAssessmentData.relatedIndicatorPaths();
        assertEquals(12, expected.size());

        List<RiskVO> risks = service.assembleRisk(FixedAssessmentData.ASSESSMENT_ID);
        assertEquals(12, risks.size());
        for (RiskVO risk : risks) {
            assertEquals(expected.get(risk.getName()), risk.getRelatedIndicator(),
                    "风险点 " + risk.getName() + " 的完整指标路径不匹配");
        }

        for (IndicatorScoreVO indicator : service.assembleIndicatorResult(assessment).getIndicatorScores()) {
            String path = indicator.getRelatedIndicator();
            assertNotNull(path, "指标 " + indicator.getIndicatorName() + " 缺少完整指标路径");
            // 路径首段是维度、末段是指标名，必须与指标自身字段自洽
            assertEquals(indicator.getDimension(), path.substring(0, path.indexOf(" > ")));
            assertTrue(path.endsWith(" > " + indicator.getIndicatorName()));
        }
    }

    /**
     * org 接口返回的 details 快照必须与 report 两个接口的实时输出完全一致，
     * 否则 org 与 report 会给出两套结果。
     */
    @Test
    void fixedDetailsSnapshotMatchesRealtimeAssembly() {
        String json = assessment.getDetails();
        assertNotNull(json);

        AssessmentGeneralDetails parsed = JSON.parseObject(json, AssessmentGeneralDetails.class);
        assertNotNull(parsed.getAssessmentDetailVO());
        assertNotNull(parsed.getIndicatorDistributionVO());

        AssessmentDetailVO realtimeGeneral = service.assembleGeneral(assessment, FixedAssessmentData.risks());
        IndicatorDistributionVO realtimeIndicator = service.assembleIndicatorResult(assessment);

        // 比较内容而不是字符串：dimensionRiskDistribution / dimensionDistributions 是枚举键 Map，
        // 实时组装用 EnumMap（键序 = 枚举声明顺序），而从 JSON 解析回来的是 HashMap，
        // 键序天然不同；JSON 对象键序没有语义，因此解析成 JSON 树后做与键序无关的深度比较。
        assertEquals(JSON.parseObject(JSON.toJSONString(realtimeGeneral)),
                JSON.parseObject(JSON.toJSONString(parsed.getAssessmentDetailVO())));
        assertEquals(JSON.parseObject(JSON.toJSONString(realtimeIndicator)),
                JSON.parseObject(JSON.toJSONString(parsed.getIndicatorDistributionVO())));

        assertEquals(FixedAssessmentData.ASSESSMENT_ID, parsed.getAssessmentDetailVO().getAssessmentId());
        assertEquals(23.58, parsed.getAssessmentDetailVO().getOverallResult().getOverallScore());
        assertEquals(12, parsed.getIndicatorDistributionVO().getTotalCount());
    }

    /**
     * 固定数据集不提供「相关法规」：判定报告只在产品标识与劳动用工两类风险点上给出法律依据，
     * 为保持 12 条口径一致，行为证据里的 relatedRegulations 一律为空列表。
     */
    @Test
    void fixedDataCarriesNoRegulations() {
        List<RiskVO> risks = service.assembleRisk(FixedAssessmentData.ASSESSMENT_ID);

        assertEquals(12, risks.size());
        for (RiskVO risk : risks) {
            assertNotNull(risk.getRelatedIndicators());
            for (com.riskwarning.common.po.risk.RelatedIndicator evidence : risk.getRelatedIndicators()) {
                for (com.riskwarning.common.po.risk.RelatedBehavior behavior : evidence.getRelatedBehaviors()) {
                    assertNotNull(behavior.getRelatedRegulations(),
                            risk.getName() + " 的相关法规应为空列表而不是 null");
                    assertTrue(behavior.getRelatedRegulations().isEmpty(),
                            risk.getName() + " 的行为证据不应再带相关法规");
                }
            }
        }
    }

    /**
     * 风险点与行为的对应关系必须与判定报告逐条对齐：一个风险点可以命中多条行为，
     * 并且同一条行为允许同时挂到多个风险点下（例如「未办理两用物项出口许可证」
     * 同时命中出口管制物项合规性与进出口许可证合规性）。
     */
    @Test
    void riskPointsKeepAlignedBehaviorEvidence() {
        List<RiskVO> risks = service.assembleRisk(FixedAssessmentData.ASSESSMENT_ID);
        assertEquals(12, risks.size());

        Map<String, Integer> expectedBehaviors = new java.util.LinkedHashMap<>();
        expectedBehaviors.put("企业合作伙伴是否存在违法违规行为", 1);
        expectedBehaviors.put("产品标识管理制度", 1);
        expectedBehaviors.put("特殊产品警示标识", 1);
        expectedBehaviors.put("加班费支付合规率", 2);
        expectedBehaviors.put("工时制度合规率", 1);
        expectedBehaviors.put("应付账款逾期情况", 3);
        expectedBehaviors.put("票据信用记录", 1);
        expectedBehaviors.put("出口管制物项合规性", 3);
        expectedBehaviors.put("进出口许可证合规性", 5);
        expectedBehaviors.put("最终用户/最终用途合规性", 1);
        expectedBehaviors.put("多清单筛查机制", 1);
        expectedBehaviors.put("清单筛查覆盖率", 1);

        int total = 0;
        for (RiskVO risk : risks) {
            List<String> descriptions = risk.getRelatedIndicators().stream()
                    .flatMap(evidence -> evidence.getRelatedBehaviors().stream())
                    .map(behavior -> behavior.getDescription())
                    .collect(java.util.stream.Collectors.toList());

            Integer expected = expectedBehaviors.get(risk.getName());
            assertNotNull(expected, "未登记的风险点：" + risk.getName());
            assertEquals(expected.intValue(), descriptions.size(),
                    risk.getName() + " 的行为条数与报告不一致");
            assertEquals(descriptions.size(), descriptions.stream().distinct().count(),
                    risk.getName() + " 出现重复行为描述");
            for (String description : descriptions) {
                assertNotNull(description);
                assertFalse(description.trim().isEmpty(), risk.getName() + " 存在空行为描述");
            }
            total += descriptions.size();
        }
        assertEquals(21, total, "行为证据总条数应为 21");
    }

    /** 12 个风险点得分都是两位小数的加权结果，看起来更像真实评估而不是整数档位。 */
    @Test
    void riskScoresKeepTwoDecimals() {        List<RiskVO> risks = service.assembleRisk(FixedAssessmentData.ASSESSMENT_ID);

        assertEquals(12, risks.size());
        for (RiskVO risk : risks) {
            Double score = risk.getScore();
            assertNotNull(score);
            assertEquals(0.0, Math.abs(Math.round(score * 100) - score * 100), 1e-9,
                    risk.getName() + " 得分超过两位小数");
            assertTrue(score % 1 != 0, risk.getName() + " 得分应带小数");
        }
    }
}
