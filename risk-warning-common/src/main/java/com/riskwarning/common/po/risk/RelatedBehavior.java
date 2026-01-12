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
public class RelatedBehavior {

    private Long projectId;

    private String description;

    private List<RelatedRegulation> relatedRegulations;
}
