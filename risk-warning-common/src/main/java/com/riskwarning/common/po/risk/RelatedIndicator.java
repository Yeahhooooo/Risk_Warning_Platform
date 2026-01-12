package com.riskwarning.common.po.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelatedIndicator {

    private String indicatorId;

    private String indicatorName;

    private Double score;

    private Double maxScore;

    private List<RelatedBehavior> relatedBehaviors;

    private boolean isPrimaryTrigger;
}
