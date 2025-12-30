package com.riskwarning.processing.batch;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.ls.LSInput;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


@Component
@StepScope
@Slf4j
public class LineRangePartitioner implements Partitioner {

    @Value("#{jobParameters['filePaths']}")
    private String filePaths;

    private final int linesPerChunk = 100;


    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Map<String, ExecutionContext> result = new HashMap<>();
        AtomicInteger counter = new AtomicInteger(0);

        List<String> paths = filePaths == null ? new ArrayList<>() : Arrays.asList(filePaths.split(","));

        // 遍历文件路径列表
        for (String filePath : paths) {
            File file = new File(filePath); // 将路径转换为 File 对象

            if (!file.exists()) {
                // 处理文件不存在的情况 (例如，抛出异常或跳过)
                System.err.println("File not found: " + filePath);
                continue;
            }

            // 假设这个方法能高效计算出文件总行数
            long totalLines = countLines(filePath);

            long startLine = 1;

            while (startLine <= totalLines) {
                long endLine = Math.min(startLine + linesPerChunk - 1, totalLines);

                // 1. 创建 ExecutionContext
                ExecutionContext context = new ExecutionContext();

                // 2. 存入 Worker Step 需要的信息
                context.putString("filePath", filePath); // 存入路径字符串
                context.putLong("startLine", startLine);
                context.putLong("endLine", endLine);

                // 3. 存入结果 Map
                String key = "partition" + counter.getAndIncrement();
                result.put(key, context);

                startLine = endLine + 1;
            }
        }
        return result;
    }

    private int countLines(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            int lines = 0;
            while (reader.readLine() != null) {
                lines++;
            }
            return lines;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

