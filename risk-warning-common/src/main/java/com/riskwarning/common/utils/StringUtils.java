package com.riskwarning.common.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class StringUtils {

    public static String generateFileName(Long projectId, String uploadId){
        return projectId + "_" + uploadId + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
}
