package com.riskwarning.knowledge.service;

import com.riskwarning.knowledge.config.VectorConfig;
import com.riskwarning.knowledge.repository.MilvusRepository;
import com.riskwarning.knowledge.util.VectorizationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量存储服务
 * 提供文本向量化并存储到Milvus的功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStorageService {
    
    private final VectorizationUtil vectorizationUtil;
    private final MilvusRepository milvusRepository;
    private final VectorConfig vectorConfig;
    

    public void storeTextVector(String collectionName, String id, String text,
                                String esId, String name, String dimension,
                                String industry, String region) {
        // 1. 确保集合存在
        milvusRepository.createCollectionIfNotExists(collectionName, vectorConfig.getDimension());
        
        // 2. 向量化文本
        float[] vector = vectorizationUtil.vectorize(text);
        
        // 3. 转换为List<Float>
        List<Float> vectorList = new ArrayList<>();
        for (float f : vector) {
            vectorList.add(f);
        }
        
        // 4. 准备数据
        List<String> ids = new ArrayList<>();
        ids.add(id);
        
        List<List<Float>> vectors = new ArrayList<>();
        vectors.add(vectorList);
        
        List<String> esIds = new ArrayList<>();
        esIds.add(esId != null ? esId : "");
        
        List<String> names = new ArrayList<>();
        names.add(name != null ? name : "");
        
        List<String> dimensions = new ArrayList<>();
        dimensions.add(dimension != null ? dimension : "");
        
        List<String> industries = new ArrayList<>();
        industries.add(industry != null ? industry : "");
        
        List<String> regions = new ArrayList<>();
        regions.add(region != null ? region : "");
        
        // 5. 插入到Milvus
        milvusRepository.insertVectors(collectionName, ids, vectors, esIds, names, dimensions, industries, regions);
      
    }
    
    /**
     * 批量存储文本向量
     */
    public void batchStoreTextVectors(String collectionName, List<VectorData> vectorDataList) {
        if (vectorDataList == null || vectorDataList.isEmpty()) {
            throw new IllegalArgumentException("向量数据列表不能为空");
        }
        
        // 1. 确保集合存在
        milvusRepository.createCollectionIfNotExists(collectionName, vectorConfig.getDimension());
        
        // 2. 批量向量化
        List<String> texts = new ArrayList<>();
        for (VectorData data : vectorDataList) {
            texts.add(data.getText());
        }
        
        List<float[]> vectors = vectorizationUtil.batchVectorize(texts);
        
        // 3. 准备数据
        List<String> ids = new ArrayList<>();
        List<List<Float>> vectorList = new ArrayList<>();
        List<String> esIds = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> dimensions = new ArrayList<>();
        List<String> industries = new ArrayList<>();
        List<String> regions = new ArrayList<>();
        
        for (int i = 0; i < vectorDataList.size(); i++) {
            VectorData data = vectorDataList.get(i);
            
            ids.add(data.getId());
            
            // 转换float[]为List<Float>
            List<Float> vec = new ArrayList<>();
            for (float f : vectors.get(i)) {
                vec.add(f);
            }
            vectorList.add(vec);
            
            esIds.add(data.getEsId() != null ? data.getEsId() : "");
            names.add(data.getName() != null ? data.getName() : "");
            dimensions.add(data.getDimension() != null ? data.getDimension() : "");
            industries.add(data.getIndustry() != null ? data.getIndustry() : "");
            regions.add(data.getRegion() != null ? data.getRegion() : "");
        }
        
        // 4. 批量插入
        milvusRepository.insertVectors(collectionName, ids, vectorList, esIds, names, dimensions, industries, regions);
    
    }
    
    /**
     * 向量数据DTO
     */
    @lombok.Data
    public static class VectorData {
        private String id;
        private String text;
        private String esId;
        private String name;
        private String dimension;
        private String industry;
        private String region;
    }
}

