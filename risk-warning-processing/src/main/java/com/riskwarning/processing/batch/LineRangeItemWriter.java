package com.riskwarning.processing.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class LineRangeItemWriter implements ItemWriter<String> {
    @Override
    public void write(List<? extends String> list) throws Exception {
        log.info("write received {} items", list.size());

        // todo: 在这里处理最终结果，将行为信息写到json文件
    }
}
