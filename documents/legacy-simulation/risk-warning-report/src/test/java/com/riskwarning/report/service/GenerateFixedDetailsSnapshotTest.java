package com.riskwarning.report.service;

import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.hardcode.FixedAssessmentData;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.report.entity.vo.AssessmentGeneralDetails;
import com.riskwarning.report.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生成 org 侧返回用的 details 快照：写死评估结果的 general + 指标分布，
 * 与原数据结构 {@link AssessmentGeneralDetails} 完全一致。
 *
 * <p>默认不参与构建，避免每次跑测试都改写源码目录、并掩盖快照一致性校验。
 * 仅当 {@link FixedAssessmentData} 里的固定数据变化时手动执行：</p>
 *
 * <pre>mvn -pl risk-warning-report test -Dtest=GenerateFixedDetailsSnapshotTest -DregenerateFixedDetails=true</pre>
 *
 * <p>生成结果由 {@code FixedAssessmentResultTest#fixedDetailsSnapshotMatchesRealtimeAssembly} 校验一致性。</p>
 */
class GenerateFixedDetailsSnapshotTest {

    @Test
    @EnabledIfSystemProperty(named = "regenerateFixedDetails", matches = "true")
    void generateSnapshot() throws Exception {
        ReportServiceImpl service = new ReportServiceImpl();
        Assessment assessment = FixedAssessmentData.assessment();
        AssessmentGeneralDetails details = new AssessmentGeneralDetails(
                service.assembleGeneral(assessment, FixedAssessmentData.risks()),
                service.assembleIndicatorResult(assessment));
        String json = JSON.toJSONString(details);

        Path target = Paths.get("..", "risk-warning-common", "src", "main", "resources",
                "hardcode", "fixed-assessment-details.json").toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        try (OutputStream out = Files.newOutputStream(target)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
        System.out.println("[snapshot] written " + target + " (" + json.length() + " chars)");
        assertTrue(Files.size(target) > 0);
    }
}
