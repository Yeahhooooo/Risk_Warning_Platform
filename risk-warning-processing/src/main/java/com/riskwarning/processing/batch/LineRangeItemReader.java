package com.riskwarning.processing.batch;

import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class LineRangeItemReader implements ItemReader<String> {

    private final BufferedReader reader;

    private String filePath;

    private int startLine;

    private int endLine;

    private int currentLine = 0;
    private String next;

    public LineRangeItemReader(String filePath, int startLine, int endLine) {
        try {
            this.reader = new BufferedReader(new FileReader(filePath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.startLine = startLine;
        this.endLine = endLine;
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

