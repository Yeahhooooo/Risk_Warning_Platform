package com.riskwarning.knowledge.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.riskwarning.knowledge.config.VectorConfig;
import com.riskwarning.knowledge.service.VectorizationService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
@ConditionalOnProperty(name = "vectorization.mode", havingValue = "api")
public class ApiVectorizationService implements VectorizationService {
    
    private final VectorConfig vectorConfig;
    private final OkHttpClient httpClient;
    
    public ApiVectorizationService(VectorConfig vectorConfig) {
        this.vectorConfig = vectorConfig;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }
    
    @Override
    public float[] vectorize(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("文本不能为空");
        }
        
        List<String> texts = Collections.singletonList(text);
        List<float[]> results = batchVectorize(texts);
        return results.get(0);
    }
    
    @Override
    public List<float[]> batchVectorize(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("文本列表不能为空");
        }
        
        try {
            String apiUrl = vectorConfig.getApi().getLocalApiUrl();
            
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("texts", texts);
            
            RequestBody body = RequestBody.create(
                    requestBody.toJSONString(),
                    MediaType.parse("application/json")
            );
            
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .post(body)
                    .build();
            
            // 发送请求
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    throw new RuntimeException("向量化API调用失败: " + response.code());
                }
                
                String responseBody = response.body().string();
                return parseResponse(responseBody);
            }
            
        } catch (IOException e) {
            throw new RuntimeException("向量化失败", e);
        }
    }
    
    private List<float[]> parseResponse(String responseBody) {
        try {
            JSONArray jsonArray = JSON.parseArray(responseBody);
            List<float[]> vectors = new ArrayList<>();
            
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONArray vectorArray = jsonArray.getJSONArray(i);
                float[] vector = new float[vectorArray.size()];
                for (int j = 0; j < vectorArray.size(); j++) {
                    vector[j] = vectorArray.getFloatValue(j);
                }
                vectors.add(vector);
            }
            
            return vectors;
        } catch (Exception e) {
            throw new RuntimeException("解析向量化响应失败", e);
        }
    }
}

