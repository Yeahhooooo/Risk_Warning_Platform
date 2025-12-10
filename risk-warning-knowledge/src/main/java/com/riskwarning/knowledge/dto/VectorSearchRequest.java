package com.riskwarning.knowledge.dto;

import lombok.Data;


@Data
public class VectorSearchRequest {

    private String queryText;//查询文本

    private String collectionType;//集合类型："indicator" 或 "regulation"
    
    private Integer topK = 10;//返回top K个结果，默认10
    
    private String industry;//行业过滤（可选）
    
    private String region;//地域过滤（可选）
    
    private String dimension;//维度过滤（可选）
    
    private Double similarityThreshold;//相似度阈值
}

