package com.riskwarning.common.po.risk;


import lombok.Data;

@Data
public class RelatedRegulation {
    private String regulationId;

    private String regulationName;

    private String violationType;

    private String complianceRequirement;
}