package com.riskwarning.org.service;

import com.riskwarning.org.entity.dto.FileUploadInitResponse;
import org.springframework.web.multipart.MultipartFile;


public interface FileService {

    FileUploadInitResponse initUpload(Long projectId, String fileHash, Long fileSize, Integer totalChunks, String fileType);

    void uploadChunk(Long projectId, String uploadId, Integer chunkIndex, MultipartFile file, Long assessmentId);

    void deleteTempFile(Long projectId, String uploadId, Long assessmentId);

    Long confirmUpload(Long projectId, Long assessmentId);
}
