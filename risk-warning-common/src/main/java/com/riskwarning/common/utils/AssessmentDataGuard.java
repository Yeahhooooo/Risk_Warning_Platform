package com.riskwarning.common.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.riskwarning.common.exception.BusinessException;
import com.riskwarning.common.po.report.Assessment;

/** Prevent historical demonstration snapshots from being presented as real assessments. */
public final class AssessmentDataGuard {
    private AssessmentDataGuard() {}

    public static Assessment requireReal(Assessment assessment) {
        if (assessment == null) throw new BusinessException("评估记录不存在");
        if (assessment.getDetails() != null && !assessment.getDetails().trim().isEmpty()) {
            JSONObject root;
            try {
                root = JSON.parseObject(assessment.getDetails());
            } catch (RuntimeException e) {
                throw new BusinessException("评估详情损坏，请重新评估");
            }
            JSONObject general = root == null ? null : root.getJSONObject("assessmentDetailVO");
            // The removed simulation handler copied these two IDs into every stored snapshot.
            if (general != null && Long.valueOf(900001L).equals(general.getLong("assessmentId"))
                    && Long.valueOf(900001L).equals(general.getLong("projectId"))) {
                throw new BusinessException("该记录为历史模拟评估，请上传原始文件重新评估");
            }
        }
        return assessment;
    }
}
