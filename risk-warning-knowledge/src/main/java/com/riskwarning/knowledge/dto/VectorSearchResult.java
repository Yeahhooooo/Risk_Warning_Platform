package com.riskwarning.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchResult {
 
    private String id;

    private String esId;
 
    private String name;
    
    private Float score;
    
    private String dimension;

    private String industry;
    
    private String region;
}

