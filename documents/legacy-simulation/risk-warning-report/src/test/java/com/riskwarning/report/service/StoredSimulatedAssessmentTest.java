package com.riskwarning.report.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.common.enums.risk.RiskLevelEnum;
import com.riskwarning.common.hardcode.FixedAssessmentData;
import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.report.entity.vo.general.AssessmentDetailVO;
import com.riskwarning.report.entity.vo.indicator.IndicatorDistributionVO;
import com.riskwarning.report.entity.vo.risk.RiskVO;
import com.riskwarning.report.repository.AssessmentRepository;
import com.riskwarning.report.repository.IndicatorResultRepository;
import com.riskwarning.report.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 回归测试：模拟评估流程落库真实记录（id 非 900001）后，report 三个接口必须仍返回固定模拟结论。
 *
 * <p>背景：模拟评估产生的记录里，{@code details} 存的是「写入当时」的快照文本。
 * 固定数据集调整过一次（风险点 16 条 → 12 条、总分 23.81 → 23.58），所以库里的旧快照
 * 与当前 classpath 快照既不相等，曾经导致 {@code isSimulated} 失手：general 直接反序列化旧快照
 * （16 条风险、总分被重算为 null），indicatorResult / risk 则去查库与 ES，返回空结果。
 * 现在改为读快照内部的固定 assessmentId / projectId 标记，历史记录也能被正确识别。</p>
 */
class StoredSimulatedAssessmentTest {

    /** 模拟评估落库产生的真实记录（对应本地库中的 project 349）。 */
    private static final Long STORED_ID = 425L;
    private static final Long STORED_PROJECT_ID = 349L;
    private static final LocalDateTime STORED_DATE = LocalDateTime.of(2026, 9, 20, 15, 59, 15);

    private final AssessmentRepository assessmentRepository = mock(AssessmentRepository.class);
    private final IndicatorResultRepository indicatorResultRepository = mock(IndicatorResultRepository.class);
    private final ReportServiceImpl service = new ReportServiceImpl();

    StoredSimulatedAssessmentTest() {
        ReflectionTestUtils.setField(service, "assessmentRepository", assessmentRepository);
        ReflectionTestUtils.setField(service, "indicatorResultRepository", indicatorResultRepository);
    }

    /** 构造一条「数据集调整前落库」的模拟记录：内容仍是固定数据集，但与当前快照文本不相等。 */
    private Assessment staleSimulatedRecord() {
        JSONObject snapshot = JSON.parseObject(FixedAssessmentData.assessment().getDetails());
        JSONObject general = snapshot.getJSONObject("assessmentDetailVO");
        // 旧快照的口径：16 个风险点、总分 23.81
        general.getJSONObject("overallResult").put("overallScore", 23.81);
        general.getJSONObject("riskSummary").put("totalRisks", 16);
        general.getJSONObject("indicatorOverview").put("behaviorIndicators", 16);

        Assessment stored = Assessment.builder()
                .id(STORED_ID)
                .projectId(STORED_PROJECT_ID)
                .assessmentDate(STORED_DATE)
                .overallScore(23.81)
                .overallRiskLevel(RiskLevelEnum.MEDIUM_RISK)
                .status(AssessmentStatusEnum.ASSESSED)
                .details(JSON.toJSONString(snapshot))
                .build();
        when(assessmentRepository.findById(STORED_ID)).thenReturn(Optional.of(stored));
        return stored;
    }

    @Test
    void generalKeepsFixedResultButUsesStoredIdentity() {
        Assessment stored = staleSimulatedRecord();

        AssessmentDetailVO general = service.assembleGeneral(stored);

        // 内容 = 当前固定数据集（不是库里的旧快照）
        assertEquals(23.58, general.getOverallResult().getOverallScore());
        assertEquals(RiskLevelEnum.MEDIUM_RISK, general.getOverallResult().getOverallRiskLevel());
        assertEquals(12, general.getRiskSummary().getTotalRisks());
        assertEquals(4, general.getRiskSummary().getHighRiskCount());
        assertEquals(8, general.getRiskSummary().getMediumRiskCount());
        assertEquals(12, general.getIndicatorOverview().getBehaviorIndicators());
        // 身份 = 本次触发的真实记录，前端据此对齐项目与评估时间
        assertEquals(STORED_ID, general.getAssessmentId());
        assertEquals(STORED_PROJECT_ID, general.getProjectId());
        assertEquals(STORED_DATE, general.getAssessmentDate());
    }

    @Test
    void indicatorResultKeepsTwelveFixedIndicators() {
        Assessment stored = staleSimulatedRecord();

        IndicatorDistributionVO result = service.assembleIndicatorResult(stored);

        assertEquals(STORED_ID, result.getAssessmentId());
        assertEquals(12, result.getTotalCount());
        assertEquals(12, result.getIndicatorScores().size());
        assertEquals(23.58, result.getTotalScore());
    }

    @Test
    void riskKeepsTwelveFixedRisks() {
        staleSimulatedRecord();

        List<RiskVO> risks = service.assembleRisk(STORED_ID);

        assertEquals(12, risks.size());
        assertNotNull(risks.get(0).getName());
        assertEquals(30.25, risks.get(0).getScore());
    }

    /** 真实评估（details 内部是自身 id）不能被误判成模拟评估，仍走原有查库 / 查 ES 口径。 */
    @Test
    void genuineAssessmentIsNotTreatedAsSimulated() {
        JSONObject snapshot = JSON.parseObject(FixedAssessmentData.assessment().getDetails());
        snapshot.getJSONObject("assessmentDetailVO").put("assessmentId", STORED_ID);
        snapshot.getJSONObject("assessmentDetailVO").put("projectId", STORED_PROJECT_ID);
        Assessment real = Assessment.builder()
                .id(STORED_ID).projectId(STORED_PROJECT_ID).assessmentDate(STORED_DATE)
                .overallScore(47.36).overallRiskLevel(RiskLevelEnum.HIGH_RISK)
                .status(AssessmentStatusEnum.ASSESSED)
                .details(JSON.toJSONString(snapshot))
                .build();

        List<IndicatorResult> rows = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            rows.add(IndicatorResult.builder().indicatorEsId("real-" + i).indicatorName("真实指标" + i)
                    .dimension("企业信用风险")
                    .calculatedScore(40.0).maxPossibleScore(100.0).build());
        }
        when(indicatorResultRepository.findByAssessmentId(STORED_ID)).thenReturn(rows);

        IndicatorDistributionVO result = service.assembleIndicatorResult(real);

        assertEquals(3, result.getTotalCount());
        assertEquals(3, result.getIndicatorScores().size());
        assertEquals(40.0, result.getTotalScore());
    }
}
