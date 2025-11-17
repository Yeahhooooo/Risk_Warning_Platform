package com.riskwarning.common.enums;


import lombok.Data;
import lombok.Getter;

@Getter
public enum DataSourceTypeEnum {

    KNOWLEDGE_QUERY("knowledge_query", "知识库查询"),

    FILE_UPLOAD("file_upload", "文件上传"),

    QUESTIONNAIRE("questionnaire", "问卷调查");

    private final String code;

    private final String description;

    DataSourceTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
