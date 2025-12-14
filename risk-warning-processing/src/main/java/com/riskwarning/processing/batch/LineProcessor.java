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
        return item;
    }
}

