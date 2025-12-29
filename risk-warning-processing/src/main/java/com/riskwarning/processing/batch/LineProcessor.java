package com.riskwarning.processing.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LineProcessor implements ItemProcessor<String, String> {

    @Override
    public String process(String item) {
        log.info("Processing line: {}", item);
        // todo: 在这个地方实现具体文本信息的过滤，将一些非行为的文本过滤掉
        if(item == null || item.isEmpty()) {
            return null;
        }
        return item;
    }
}

