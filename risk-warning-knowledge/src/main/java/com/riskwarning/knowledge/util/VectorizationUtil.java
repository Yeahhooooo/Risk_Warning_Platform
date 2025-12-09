package com.riskwarning.knowledge.util;

import com.riskwarning.knowledge.config.VectorConfig;
import com.riskwarning.knowledge.service.VectorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Component
@RequiredArgsConstructor
public class VectorizationUtil {
    
    private final VectorizationService vectorizationService;
    private final VectorConfig vectorConfig;
    
   
    public float[] vectorize(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("文本不能为空");
        }
        
        // 文本预处理
        String processedText = preprocessText(text);
        
        // 调用向量化服务
        return vectorizationService.vectorize(processedText);
    }
    
    
    public List<float[]> batchVectorize(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("文本列表不能为空");
        }
        
        // 预处理
        List<String> processedTexts = texts.stream()
                .map(this::preprocessText)
                .collect(Collectors.toList());
        
        // 批量处理
        return vectorizationService.batchVectorize(processedTexts);
    }
    
   
    private String preprocessText(String text) {
        // 去除多余空白
        String processed = text.trim().replaceAll("\\s+", " ");
        
        // 截断到最大长度
        int maxLength = vectorConfig.getMaxSequenceLength();
        if (processed.length() > maxLength) {
            processed = processed.substring(0, maxLength);
        }
        
        return processed;
    }
    
    
    public int getDimension() {
        return vectorConfig.getDimension();
    }
}

