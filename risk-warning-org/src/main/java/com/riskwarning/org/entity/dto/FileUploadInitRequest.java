package com.riskwarning.org.entity.dto;

import lombok.Data;
import lombok.NonNull;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.Max;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
public class FileUploadInitRequest {

    @NotNull
    private Long projectId;

    @NotEmpty
    private String fileHash;

    @NotNull
    @Max(1024 * 1024 * 10) // 最大10MB
    private Long fileSize;

    @NotNull
    private Integer totalChunks;

    @NotEmpty
    private String fileType;


}
