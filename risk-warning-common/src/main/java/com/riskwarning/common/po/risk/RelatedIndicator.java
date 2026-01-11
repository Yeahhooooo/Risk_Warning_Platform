package com.riskwarning.common.po.risk;

import lombok.Data;

@Data
public class RelatedIndicator {

    private String indicatorId;

    private String indicatorName;

    private Double score;

    private Double threshold;

    private boolean isPrimaryTrigger;
}
