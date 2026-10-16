package com.riskwarning.org.controller;


import com.riskwarning.common.annotation.AuthRequired;
import com.riskwarning.common.result.Result;
import com.riskwarning.org.entity.dto.FileUploadChunkRequest;
import com.riskwarning.org.entity.dto.FileUploadInitRequest;
import com.riskwarning.org.entity.dto.FileUploadInitResponse;
import com.riskwarning.org.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {


    @Autowired
    private FileService fileService;

    @PostMapping("/initUpload")
    @AuthRequired
    public Result initUpload(@Valid @RequestBody FileUploadInitRequest fileUploadInitRequest) {
        FileUploadInitResponse upload = fileService.initUpload(
                fileUploadInitRequest.getProjectId(),
                fileUploadInitRequest.getFileHash(),
                fileUploadInitRequest.getFileSize(),
                fileUploadInitRequest.getTotalChunks(),
                fileUploadInitRequest.getFileType()
        );
        return Result.success(upload);
    }

    @PostMapping("/uploadChunk")
    @AuthRequired
    public Result uploadChunk(@Valid FileUploadChunkRequest fileUploadChunkRequest) {
        fileService.uploadChunk(
                fileUploadChunkRequest.getProjectId(),
                fileUploadChunkRequest.getUploadId(),
                fileUploadChunkRequest.getChunkIndex(),
                fileUploadChunkRequest.getChunkData(),
                fileUploadChunkRequest.getAssessmentId()
        );
        return Result.success("分片上传成功");
    }

    @PostMapping("/confirmUpload")
    @AuthRequired
    public Result confirmUpload(@RequestParam @NotNull Long projectId, @RequestParam @NotNull Long assessmentId) {
        log.info("[AssessmentFlow] stage=UPLOAD_CONFIRM status=START projectId={}", projectId);
        try {
            Long confirmedId = fileService.confirmUpload(projectId, assessmentId);
            log.info("[AssessmentFlow] stage=UPLOAD_CONFIRM status=RETURNED projectId={} checkStage=UPLOAD_QUEUE_ENQUEUE", projectId);
            return Result.success(java.util.Collections.singletonMap("assessmentId", confirmedId));
        } catch (RuntimeException failure) {
            log.error("[AssessmentFlow] stage=UPLOAD_CONFIRM status=FAILED projectId={}", projectId, failure);
            throw failure;
        }
    }

    @PostMapping("/deleteFile")
    @AuthRequired
    public Result deleteFile(@RequestParam @NotNull Long projectId, @RequestParam @NotEmpty String uploadId,
                             @RequestParam @NotNull Long assessmentId) {
        fileService.deleteTempFile(projectId, uploadId, assessmentId);
        return Result.success("文件删除成功");
    }
}
