package com.riskwarning.org.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UploadFileDto implements Serializable {

    private static final long serialVersionUID = 112312L;

    private String uploadId;

    private Long projectId;

    private Long userId;

    private String fileHash;

    // 目前已上传的分片索引集合
    private Set<Integer> chunkIndexSet;

    // 总分片数
    private Integer totalChunks;

    // 文件总大小·
    private Long fileSize;

    private String filePath;

    private String fileSuffix;
}
