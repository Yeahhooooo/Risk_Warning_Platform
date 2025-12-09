package com.riskwarning.knowledge.util;

import com.riskwarning.knowledge.config.VectorConfig;
import com.riskwarning.knowledge.repository.ElasticsearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Component
@RequiredArgsConstructor
public class EsVectorizationUtil {
    
    private final ElasticsearchRepository esRepository;
    private final VectorizationUtil vectorizationUtil;
    private final VectorConfig vectorConfig;
    
    // 批量处理大小
    private static final int BATCH_SIZE = 50;
    
    public VectorizationResult vectorizeIndicators() {
        return vectorizeIndex(
                "t_indicator",
                "name",
                "name_vector",
                doc -> {
                    Map<String, Object> source = doc.getSource();
                    if (source != null) {
                        Object nameObj = source.get("name");
                        return nameObj != null ? nameObj.toString() : null;
                    }
                    return null;
                }
        );
    }
    
    
    public VectorizationResult vectorizeRegulations() {
        return vectorizeIndex(
                "t_regulation",
                "full_text",
                "full_text_vector",
                doc -> {
                    Map<String, Object> source = doc.getSource();
                    if (source != null) {
                        Object fullTextObj = source.get("full_text");
                        return fullTextObj != null ? fullTextObj.toString() : null;
                    }
                    return null;
                }
        );
    }
    
    public VectorizationResult vectorizeBehaviors() {
        return vectorizeIndex(
                "t_behavior",
                "description",
                "description_vector",
                doc -> {
                    Map<String, Object> source = doc.getSource();
                    if (source != null) {
                        Object descObj = source.get("description");
                        return descObj != null ? descObj.toString() : null;
                    }
                    return null;
                }
        );
    }
    
    private VectorizationResult vectorizeIndex(String indexName, 
                                               String textFieldName,
                                               String vectorFieldName,
                                               TextExtractor textExtractor) {
        log.info("开始向量化索引: {}, 字段: {}", indexName, textFieldName);
        
        VectorizationResult result = new VectorizationResult();
        result.setIndexName(indexName);
        result.setTextFieldName(textFieldName);
        result.setVectorFieldName(vectorFieldName);
        result.setStartTime(System.currentTimeMillis());
        
        try {
            // 1. 获取文档总数
            long totalCount = esRepository.getDocumentCount(indexName);
            result.setTotalCount(totalCount);

            
            if (totalCount == 0) {
                result.setSuccessCount(0);
                result.setFailedCount(0);
                result.setEndTime(System.currentTimeMillis());
                return result;
            }
            
            // 2. 分页处理
            int processedCount = 0;
            int from = 0;
            int pageSize = BATCH_SIZE;
            
            while (from < totalCount) {
                double progress = totalCount > 0 ? (double) processedCount / totalCount * 100 : 0;
                log.info("处理进度: {}/{} ({:.2f}%)", 
                        processedCount, totalCount, progress);
                
                // 2.1 查询当前页文档
                String[] sourceFields = {textFieldName};
                @SuppressWarnings("unchecked")
                List<ElasticsearchRepository.DocumentWithId<Map<String, Object>>> documents = 
                        (List<ElasticsearchRepository.DocumentWithId<Map<String, Object>>>) 
                        (List<?>) esRepository.searchDocuments(indexName, from, pageSize, sourceFields, Map.class);
                
                if (documents.isEmpty()) {
                    break;
                }
                
                // 2.2 提取文本并过滤空值
                List<DocumentText> documentTexts = new ArrayList<>();
                for (ElasticsearchRepository.DocumentWithId<Map<String, Object>> doc : documents) {
                    String text = textExtractor.extract(doc);
                    if (text != null && !text.trim().isEmpty()) {
                        DocumentText docText = new DocumentText();
                        docText.setDocumentId(doc.getId());
                        docText.setText(text);
                        documentTexts.add(docText);
                    }
                }
                
                if (documentTexts.isEmpty()) {
                    from += pageSize;
                    continue;
                }
                
                // 2.3 批量向量化
                List<String> texts = documentTexts.stream()
                        .map(DocumentText::getText)
                        .collect(Collectors.toList());
                
                List<float[]> vectors;
                try {
                    vectors = vectorizationUtil.batchVectorize(texts);
                } catch (Exception e) {
                    result.setFailedCount(result.getFailedCount() + documentTexts.size());
                    from += pageSize;
                    continue;
                }
                
                // 2.4 准备更新数据
                List<ElasticsearchRepository.VectorUpdate> updates = new ArrayList<>();
                for (int i = 0; i < documentTexts.size(); i++) {
                    DocumentText docText = documentTexts.get(i);
                    float[] vector = vectors.get(i);
                    
                    // 验证向量有效性
                    if (vector == null || vector.length == 0) {
                        result.setFailedCount(result.getFailedCount() + 1);
                        continue;
                    }
                    
                    // 验证向量维度
                    int expectedDimension = vectorConfig.getDimension();
                    if (vector.length != expectedDimension) {
                        log.warn("文档 {} 的向量维度不正确: 期望={}, 实际={}", 
                                docText.getDocumentId(), expectedDimension, vector.length);
                        result.setFailedCount(result.getFailedCount() + 1);
                        continue;
                    }
                    
                    // 转换为List<Float>
                    List<Float> vectorList = new ArrayList<>();
                    for (float f : vector) {
                        vectorList.add(f);
                    }
                    
                    ElasticsearchRepository.VectorUpdate update = 
                            new ElasticsearchRepository.VectorUpdate();
                    update.setDocumentId(docText.getDocumentId());
                    update.setVectorFieldName(vectorFieldName);
                    update.setVector(vectorList);
                    updates.add(update);
                }
                
                if (updates.isEmpty()) {
                    from += pageSize;
                    continue;
                }
                
                // 2.5 批量更新ES
                try {
                    esRepository.batchUpdateVectorFields(indexName, updates);
                    result.setSuccessCount(result.getSuccessCount() + updates.size());
                } catch (Exception e) {
                    result.setFailedCount(result.getFailedCount() + updates.size());
                }
                
                processedCount += documents.size();
                from += pageSize;
                
                // 避免请求过快，稍微延迟
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            result.setEndTime(System.currentTimeMillis());
            long duration = result.getEndTime() - result.getStartTime();
            
        } catch (Exception e) {
            result.setError(e.getMessage());
            result.setEndTime(System.currentTimeMillis());
        }
        
        return result;
    }
    
   
    @FunctionalInterface
    private interface TextExtractor {
        String extract(ElasticsearchRepository.DocumentWithId<Map<String, Object>> document);
    }
    
  
    @lombok.Data
    private static class DocumentText {
        private String documentId;
        private String text;
    }
    
    @lombok.Data
    public static class VectorizationResult {
        private String indexName;
        private String textFieldName;
        private String vectorFieldName;
        private long totalCount;
        private long successCount;
        private long failedCount;
        private long startTime;
        private long endTime;
        private String error;
        
        public long getDuration() {
            return endTime > startTime ? endTime - startTime : 0;
        }
        
        public double getSuccessRate() {
            return totalCount > 0 ? (double) successCount / totalCount * 100 : 0;
        }
    }
}

