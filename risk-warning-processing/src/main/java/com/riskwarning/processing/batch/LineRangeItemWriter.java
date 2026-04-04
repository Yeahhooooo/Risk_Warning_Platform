package com.riskwarning.processing.batch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.riskwarning.common.po.behavior.Behavior;
import com.riskwarning.processing.client.ClassifierClient;
import com.riskwarning.processing.client.VectorizationClient;
import com.riskwarning.processing.dto.ClassificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Component
@StepScope
@Slf4j
public class LineRangeItemWriter implements ItemWriter<Behavior> {

    @Autowired
    private ElasticsearchClient elasticsearchClient;
    
    @Autowired
    private ClassifierClient classifierClient;
    
    @Autowired
    private VectorizationClient vectorizationClient;


    @Override
    public void write(List<? extends Behavior> list) throws Exception {
        log.info("write received {} items", list.size());

        int retryTimes = 3;
        while (retryTimes > 0) {
            try {
                // 准备批量分类请求
                List<Map<String, String>> items = new ArrayList<>();
                for (Behavior behavior : list) {
                    if (behavior.getDescription() != null && !behavior.getDescription().trim().isEmpty()) {
                        Map<String, String> item = new HashMap<>();
                        item.put("text", behavior.getDescription());
                        item.put("input_type", "behavior");  // behavior 类型
                        items.add(item);
                    }
                }
                
                if (items.isEmpty()) {
                    log.warn("没有有效的文本进行分类，跳过分类步骤");
                } else {
                    // 调用分类服务
                    Map<String, Object> request = new HashMap<>();
                    request.put("items", items);
                    
                    Map<String, Object> classifyResponse = classifierClient.classifyBatch(request);
                    ClassificationResult classificationResult = ClassificationResult.fromMap(classifyResponse);
                    
                    // 将分类结果设置到 Behavior 对象
                    if (classificationResult.getResults() != null 
                            && classificationResult.getResults().size() == items.size()) {
                        int validIndex = 0;
                        for (int i = 0; i < list.size(); i++) {
                            Behavior behavior = list.get(i);
                            if (behavior.getDescription() != null && !behavior.getDescription().trim().isEmpty()) {
                                ClassificationResult.ItemResult itemResult = classificationResult.getResults().get(validIndex);
                                behavior.setTags(itemResult.getTags());
                                behavior.setType(itemResult.getType());
                                behavior.setDimension(itemResult.getDimension());
                                // status 字段分类服务不返回，保持为 null 或后续单独处理
                                validIndex++;
                            }
                        }
                        log.info("成功为 {} 条行为数据完成分类", validIndex);
                    } else {
                        log.warn("分类结果数量 ({}) 与请求数量 ({}) 不匹配", 
                                classificationResult.getResults() != null ? classificationResult.getResults().size() : 0,
                                items.size());
                    }
                }

                // 调用向量化服务生成向量
                List<String> textsToVectorize = new ArrayList<>();
                for (Behavior behavior : list) {
                    if (behavior.getDescription() != null && !behavior.getDescription().trim().isEmpty()) {
                        textsToVectorize.add(behavior.getDescription());
                    }
                }
                
                if (!textsToVectorize.isEmpty()) {
                    try {
                        Map<String, List<String>> vectorizeRequest = new HashMap<>();
                        vectorizeRequest.put("texts", textsToVectorize);
                        
                        Map<String, Object> vectorizeResponse = vectorizationClient.batchVectorize(vectorizeRequest);
                        
                        if (vectorizeResponse != null && Boolean.TRUE.equals(vectorizeResponse.get("success"))) {
                            @SuppressWarnings("unchecked")
                            List<List<Double>> vectors = (List<List<Double>>) vectorizeResponse.get("vectors");
                            
                            if (vectors != null && vectors.size() == textsToVectorize.size()) {
                                int vectorIndex = 0;
                                for (Behavior behavior : list) {
                                    if (behavior.getDescription() != null && !behavior.getDescription().trim().isEmpty()) {
                                        List<Double> doubleVector = vectors.get(vectorIndex);
                                        // 将 Double 转换为 Float
                                        List<Float> floatVector = new ArrayList<>(doubleVector.size());
                                        for (Double d : doubleVector) {
                                            floatVector.add(d != null ? d.floatValue() : 0.0f);
                                        }
                                        behavior.setDescriptionVector(floatVector);
                                        vectorIndex++;
                                    }
                                }
                                log.info("成功为 {} 条行为数据生成向量", vectorIndex);
                            } else {
                                log.warn("向量化结果数量 ({}) 与请求数量 ({}) 不匹配", 
                                        vectors != null ? vectors.size() : 0, textsToVectorize.size());
                            }
                        } else {
                            log.error("向量化服务调用失败: {}", vectorizeResponse);
                        }
                    } catch (Exception e) {
                        log.error("调用向量化服务失败", e);
                        // 向量化失败不影响数据写入，只是没有向量
                    }
                }

                // 写入es前检查数据
                for (Behavior behavior : list) {
                    log.debug("准备写入 ES 的行为数据: id={}, description={}, vectorSize={}", 
                            behavior.getId(), 
                            behavior.getDescription() != null ? behavior.getDescription().substring(0, Math.min(50, behavior.getDescription().length())) : "null",
                            behavior.getDescriptionVector() != null ? behavior.getDescriptionVector().size() : 0);
                    
                    // 检查向量字段
                    if (behavior.getDescriptionVector() != null) {
                        for (int i = 0; i < behavior.getDescriptionVector().size(); i++) {
                            Float val = behavior.getDescriptionVector().get(i);
                            if (val == null || Float.isNaN(val) || Float.isInfinite(val)) {
                                log.warn("行为 {} 的向量包含无效值: index={}, value={}", behavior.getId(), i, val);
                            }
                        }
                    }
                }

                // 写入es
                try {
                    BulkResponse response = elasticsearchClient.bulk(b -> {
                        for (Behavior behavior : list) {
                            b.operations(op -> op
                                    .index(idx -> idx
                                            .index("t_behavior")
                                            .document(behavior)
                                    )
                            );
                        }
                        return b;
                    });

                    if (response.errors()) {
                        log.error("Bulk insert encountered errors: {}", response.items().toString());
                        throw new Exception("Bulk insert to Elasticsearch failed");
                    }
                } catch (jakarta.json.JsonException e) {
                    log.error("JSON 序列化失败，尝试逐条写入", e);
                    
                    // 尝试逐条写入，找出问题数据
                    for (Behavior behavior : list) {
                        try {
                            elasticsearchClient.index(i -> i
                                    .index("t_behavior")
                                    .document(behavior)
                                    .id(behavior.getId())
                            );
                            log.info("成功写入行为: id={}", behavior.getId());
                        } catch (Exception ex) {
                            log.error("写入行为失败: id={}, description={}", 
                                    behavior.getId(), 
                                    behavior.getDescription(), ex);
                        }
                    }
                    throw e;
                }

                break;
            } catch (Exception e) {
                log.error("Error writing items, retries left: {}", retryTimes - 1, e);
                retryTimes--;
                if (retryTimes == 0) {
                    throw e;
                }
            }
        }
    }
}
