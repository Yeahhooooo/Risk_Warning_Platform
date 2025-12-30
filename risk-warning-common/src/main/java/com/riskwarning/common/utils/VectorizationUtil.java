package com.riskwarning.common.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Slf4j
public class VectorizationUtil {

    private static final Integer DIMENSION = 768;
    private static final Integer BATCH_SIZE = 32;
    private static final Integer MAX_SEQUENCE_LENGTH = 512;
    private static final String MODE = "api";
    private static final Double SIMILARITY_THRESHOLD = 0.3;
    private static final String BERT_API_URL = "http://localhost:8000/encode";
    private static final String API_KEY = "";
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    
   
    public static float[] vectorize(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("文本不能为空");
        }
        List<String> texts = Collections.singletonList(preprocessText(text));
        List<float[]> results = batchVectorize(texts);
        return results.get(0);
    }
    
    
    public static List<float[]> batchVectorize(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("文本列表不能为空");
        }
        
        // 预处理
        List<String> processedTexts = texts.stream()
                .map(VectorizationUtil::preprocessText)
                .collect(Collectors.toList());

        try {

            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("texts", processedTexts);

            RequestBody body = RequestBody.create(
                    requestBody.toJSONString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(BERT_API_URL)
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
    
   
    private static String preprocessText(String text) {
        // 去除多余空白
        String processed = text.trim().replaceAll("\\s+", " ");
        
        // 截断到最大长度
        if (processed.length() > MAX_SEQUENCE_LENGTH) {
            processed = processed.substring(0, MAX_SEQUENCE_LENGTH);
        }
        
        return processed;
    }


    private static List<float[]> parseResponse(String responseBody) {
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

