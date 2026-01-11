package com.riskwarning.common.po.risk;


import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class Risk {

    private String id;

    private Long projectId;

    private Long assessmentId;

    private String name;

    private String dimension;

    private String description;

    private String riskLevel;

    private double probability;

    private double impact;

    private double detectability;

    private String status;

    private String responsibleParty;

    private String[] affectedObjects;

    private String impactScope;

    private String countermeasures;

    private List<RelatedIndicator> relatedIndicators;

    private List<RelatedRegulation> relatedRegulations;

    private LocalDateTime createAt;
}
