package com.riskwarning.processing.entity.dto;

import com.riskwarning.common.enums.FileTypeEnum;
import com.riskwarning.common.po.risk.RelatedIndicator;
import com.riskwarning.processing.util.ContentExtractor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
public class DocumentProcessingResult {
    private String fileName;
    private FileTypeEnum fileType;
    private long fileSize;
    private int totalPages;
    private long totalCharacters;
    private List<ContentExtractor.TextSegment> textSegments;
    private boolean success;
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MappingResult {
        private String behaviorId;
        private Map<String, RelatedIndicator> relatedIndicators;
        private List<String> warnings;
    }
}
