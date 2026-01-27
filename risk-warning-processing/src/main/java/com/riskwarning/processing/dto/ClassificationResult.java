package com.riskwarning.processing.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 分类结果 DTO
 */
@Data
public class ClassificationResult {
    
    private List<ItemResult> results;
    
    @Data
    public static class ItemResult {
        private List<String> tags;
        private String type;  // 定性/定量
        private String dimension;
        private List<String> industry;  // 仅 indicator 和 regulation 有
    }
    
    
    public static ClassificationResult fromMap(Map<String, Object> response) {
        ClassificationResult result = new ClassificationResult();
        // Feign 返回的格式: {"results": [{"tags": [...], "type": "...", ...}, ...]}
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultsList = (List<Map<String, Object>>) response.get("results");
        
        if (resultsList != null) {
            result.results = resultsList.stream()
                .map(item -> {
                    ItemResult itemResult = new ItemResult();
                    itemResult.setTags((List<String>) item.get("tags"));
                    itemResult.setType((String) item.get("type"));
                    itemResult.setDimension((String) item.get("dimension"));
                    itemResult.setIndustry((List<String>) item.get("industry"));
                    return itemResult;
                })
                .collect(java.util.stream.Collectors.toList());
        }
        
        return result;
    }
}

