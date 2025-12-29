package com.riskwarning.processing.batch;

import com.riskwarning.common.po.behavior.Behavior;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

@Component
@StepScope
@Slf4j
public class LineProcessor implements ItemProcessor<String, Behavior> {


    @Value("#{jobParameters['projectId']}")
    private Long projectId;

    @Override
    public Behavior process(String item) {
        log.info("Processing line: {}", item);
        // todo: 在这个地方实现具体文本信息的过滤，将一些非行为的文本过滤掉
        if(item == null || item.isEmpty()) {
            return null;
        }

        // todo: 在这里可以引入本地化部署的模型用于分类/Bert向量计算，目前是远程请求
        return Behavior.builder()
                .id(UUID.randomUUID().toString())
                .projectId(projectId)
                .description(item)
                .type("")
                .dimension("")
                .tags(new ArrayList<>())
                .status("")
                .quantitativeData(0.0)
                .behaviorDate(LocalDateTime.now())
                .descriptionVector(null)
                .createdAt(LocalDateTime.now())
                .build();
    }
}

