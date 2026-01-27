package com.riskwarning.processing.batch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.riskwarning.common.po.behavior.Behavior;
import com.riskwarning.processing.client.ClassifierClient;
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

                // 写入es
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
