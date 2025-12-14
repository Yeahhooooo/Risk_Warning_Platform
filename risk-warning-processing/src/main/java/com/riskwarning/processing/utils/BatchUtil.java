package com.riskwarning.processing.utils;

import cn.hutool.json.JSONArray;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.riskwarning.common.po.behavior.Behavior;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class BatchUtil {





    public static void main(String[] args) throws IOException {

        File file = new File("/Users/huayecai/Desktop/bach_01/Risk_Warning_Platform/documents/data/behavior.json");


        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new JavaTimeModule());
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);


        List<Behavior> events = mapper.readValue(
                file,
                new TypeReference<List<Behavior>>() {}
        );

        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter("/Users/huayecai/Desktop/bach_01/Risk_Warning_Platform/documents/intern/behavior.txt")
        )) {
            for(Behavior b : events){
                writer.write(b.getDescription());
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
