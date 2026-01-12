package com.riskwarning.processing.dto;

import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.po.risk.RelatedIndicator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappingResult {
    private String behaviorId;
    private Map<String, RelatedIndicator> relatedIndicators;
    private List<String> warnings;
}
