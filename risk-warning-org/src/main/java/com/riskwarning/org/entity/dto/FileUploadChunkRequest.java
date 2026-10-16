package com.riskwarning.org.entity.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
public class FileUploadChunkRequest {

    @NotNull
    private Long projectId;

    @NotNull
    private Long assessmentId;

    @NotEmpty
    private String uploadId;

    @NotNull
    private Integer chunkIndex;

    @NotNull
    private MultipartFile chunkData;
}
