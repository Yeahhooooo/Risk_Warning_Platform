package com.riskwarning.report.entity.vo;

import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.report.entity.vo.general.AssessmentDetailVO;
import com.riskwarning.report.entity.vo.indicator.IndicatorDistributionVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentGeneralDetails {

    private AssessmentDetailVO assessmentDetailVO;

    private IndicatorDistributionVO indicatorDistributionVO;

}
