package com.riskwarning.common.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class StringUtils {

    public static String generateFileName(Long projectId, String uploadId){
        return projectId + "_" + uploadId + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    public static String generateMessageId(){
        return "msg_" + System.currentTimeMillis();
    }

    public static String generateTraceId(){
        return "trace_" + System.currentTimeMillis();
    }
}
