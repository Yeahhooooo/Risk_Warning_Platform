package com.riskwarning.processing.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationRequest {
    
    private List<ClassificationItem> items;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassificationItem {
        private String text;
        private String inputType;  // behavior, indicator, regulation
    }
}

