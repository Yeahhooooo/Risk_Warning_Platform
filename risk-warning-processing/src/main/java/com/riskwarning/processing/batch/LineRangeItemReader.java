package com.riskwarning.processing.batch;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;


@Component
@StepScope
public class LineRangeItemReader implements ItemReader<String> {

    private BufferedReader reader;

    @Value("#{stepExecutionContext['filePath']}")
    private String filePath;

    @Value("#{stepExecutionContext['startLine']}")
    private int startLine;

    @Value("#{stepExecutionContext['endLine']}")
    private int endLine;

    private int currentLine = 0;
    private String next;

    @PostConstruct
    public void init() throws Exception {
        this.reader = new BufferedReader(new FileReader(filePath));
    }

    @Override
    public String read() throws Exception {
        while ((next = reader.readLine()) != null) {
            currentLine++;
            if (currentLine < startLine) {
                continue;
            }
            if (currentLine > endLine) {
                return null;
            }
            return next;
        }
        return null;
    }
}

