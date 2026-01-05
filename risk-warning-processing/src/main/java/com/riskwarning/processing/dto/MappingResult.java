package com.riskwarning.processing.dto;

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
    private Map<String, Double> indicatorScores;
    private Map<String, List<String>> indicatorInfluencingRegulations;
    private List<String> warnings;
}
