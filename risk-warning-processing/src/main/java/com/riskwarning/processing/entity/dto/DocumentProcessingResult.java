package com.riskwarning.processing.entity.dto;

import com.riskwarning.common.enums.FileTypeEnum;
import com.riskwarning.processing.util.ContentExtractor;
import lombok.Data;

import java.util.List;

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
}
