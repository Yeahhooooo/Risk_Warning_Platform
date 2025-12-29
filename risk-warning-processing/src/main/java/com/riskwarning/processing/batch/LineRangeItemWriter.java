package com.riskwarning.processing.batch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.riskwarning.common.po.behavior.Behavior;
import com.riskwarning.common.utils.LLMUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Component
@StepScope
@Slf4j
public class LineRangeItemWriter implements ItemWriter<String> {


    @Value("#{jobParameters['projectId']}")
    private Long projectId;

    @Autowired
    private ElasticsearchClient elasticsearchClient;


    @Override
    public void write(List<? extends String> list) throws Exception {
        log.info("write received {} items", list.size());

        int retryTimes = 3;
        while (retryTimes > 0) {
            try {
                // todo: 批量infertags
                List<List<String>> tags = new ArrayList<>(list.size());
                // todo; 批量进行BERT向量化
                List<float[]> vectors = new ArrayList<>(list.size());
                // todo: 在这里为行为定性
                List<String> types = new ArrayList<>(list.size());
                // todo: 在这里为行为定维度
                List<String> dimensions = new ArrayList<>(list.size());
                // todo: 在这里为行为状态赋值
                List<String> statuses = new ArrayList<>(list.size());

                // todo: 在这里处理最终结果，将行为信息写到json文件
                List<Behavior> behaviors = new ArrayList<>();
                for(int i = 0; i < list.size(); i++) {
                    Behavior behavior = Behavior.builder()
                            .id(UUID.randomUUID().toString())
                            .projectId(projectId)
                            .description(list.get(i))
                            .type("")
                            .dimension("")
                            .tags(new ArrayList<>())
                            .status("")
                            .quantitativeData(0.0)
                            .behaviorDate(LocalDateTime.now())
                            .descriptionVector(null)
                            .createdAt(LocalDateTime.now())
                            .build();
                    behaviors.add(behavior);
                }

                // todo: 写入es
                BulkResponse response = elasticsearchClient.bulk(b -> {
                    for (Behavior behavior : behaviors) {
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
