package com.riskwarning.org.service.impl;

import com.riskwarning.common.constants.Constants;
import com.riskwarning.common.constants.RedisKey;
import com.riskwarning.common.context.UserContext;
import com.riskwarning.common.enums.FileTypeEnum;
import com.riskwarning.common.exception.BusinessException;
import com.riskwarning.common.message.AssessmentCompletedEventMessage;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.utils.FileUtils;
import com.riskwarning.common.utils.RedisUtil;
import com.riskwarning.org.entity.dto.FileUploadInitResponse;
import com.riskwarning.org.entity.dto.UploadConfirmDto;
import com.riskwarning.org.entity.dto.UploadFileDto;
import com.riskwarning.org.service.FileService;
import com.riskwarning.org.service.ProjectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.function.Supplier;

@Service
@Slf4j
public class FileServiceImpl implements FileService {
    @Autowired private RedisUtil redisUtil;
    @Autowired private ProjectService projectService;
    @Autowired private ApplicationEventPublisher events;

    @Override
    public FileUploadInitResponse initUpload(Long projectId, String fileHash, Long fileSize,
                                             Integer totalChunks, String fileType) {
        // Commit the assessment and timeout before touching Redis or disk.
        Assessment assessment = projectService.beginUploadAssessment(projectId);
        return observe(assessment, "UPLOAD_INIT", () -> {
            if (fileSize == null || fileSize <= 0 || fileSize > Constants.FILE_SIZE_LIMIT)
                throw new BusinessException("文件大小必须大于零且不能超过限制");
            if (totalChunks == null || totalChunks <= 0 || totalChunks > Constants.FILE_TOTAL_CHUNKS)
                throw new BusinessException("文件分片数目不合法");
            if (fileHash == null || fileHash.trim().isEmpty() || !FileTypeEnum.contains(fileType))
                throw new BusinessException("文件哈希为空或文件类型不支持");
            String metadataKey = String.format(RedisKey.REDIS_KEY_FILE_UPLOAD_INFO, projectId);
            String hashesKey = String.format(RedisKey.REDIS_KEY_FILE, projectId);
            Map<Object, Object> metadata = redisUtil.hmget(metadataKey);
            // Failed batches must not prevent uploading the same file in a new assessment.
            if (metadata != null) for (Object value : metadata.values()) {
                UploadFileDto old = (UploadFileDto) value;
                if (!Objects.equals(assessment.getId(), old.getAssessmentId())) {
                    redisUtil.hdel(metadataKey, old.getUploadId());
                    redisUtil.setRemove(hashesKey, old.getFileHash());
                }
            }
            if (redisUtil.sHasKey(hashesKey, fileHash)) throw new BusinessException("文件已存在，无法重复上传");
            if (redisUtil.sGetSetSize(hashesKey) >= Constants.FILE_AMOUNT_LIMIT)
                throw new BusinessException("项目上传文件数量已达上限");
            String uploadId = UUID.randomUUID().toString();
            UploadFileDto file = UploadFileDto.builder().projectId(projectId).uploadId(uploadId)
                    .assessmentId(assessment.getId()).taskId(assessment.getUploadTaskId())
                    .userId(UserContext.getUser().getId()).fileHash(fileHash).totalChunks(totalChunks)
                    .filePath(Constants.getTempFileDirPath(projectId, uploadId)).fileSuffix(fileType).build();
            if (redisUtil.sSetAndTime(hashesKey, RedisKey.REDIS_KEY_FILE_EXPIRE_TIME_SECONDS, fileHash) == 0)
                throw new BusinessException("初始化上传失败，请重新上传");
            if (!redisUtil.hset(metadataKey, uploadId, file, RedisKey.REDIS_KEY_UPLOAD_INFO_EXPIRE_TIME_SECONDS))
                throw new BusinessException("上传元数据保存失败，请重新上传");
            return new FileUploadInitResponse(uploadId, assessment.getId());
        });
    }

    @Override
    public void uploadChunk(Long projectId, String uploadId, Integer chunkIndex, MultipartFile part, Long assessmentId) {
        Assessment assessment = currentAssessment(projectId, assessmentId);
        observe(assessment, "UPLOAD_CHUNK", () -> {
            UploadFileDto file = file(assessment, uploadId);
            if (chunkIndex == null || chunkIndex < 0 || chunkIndex >= file.getTotalChunks())
                throw new BusinessException("分片索引不合法");
            if (part == null || part.isEmpty()) throw new BusinessException("文件分片不能为空");
            File target = new File(file.getFilePath(), String.valueOf(chunkIndex));
            String chunksKey = String.format(RedisKey.REDIS_KEY_UPLOAD_CHUNKS, uploadId);
            if (redisUtil.sHasKey(chunksKey, chunkIndex) && target.isFile() && target.length() > 0) return null;
            FileUtils.transferFile(part, target.getAbsolutePath());
            if (redisUtil.sSetAndTime(chunksKey, RedisKey.REDIS_KEY_UPLOAD_CHUNKS_EXPIRE_TIME_SECONDS, chunkIndex) == 0
                    && !redisUtil.sHasKey(chunksKey, chunkIndex))
                throw new BusinessException("文件分片状态保存失败");
            return null;
        });
    }

    @Override
    public Long confirmUpload(Long projectId, Long assessmentId) {
        Assessment assessment = currentAssessment(projectId, assessmentId);
        return observe(assessment, "UPLOAD_CONFIRM", () -> {
            List<UploadFileDto> files = currentFiles(assessment);
            if (files.isEmpty()) throw new BusinessException("上传任务不存在或已过期");
            for (UploadFileDto file : files) {
                Set<Object> chunks = redisUtil.sGet(String.format(RedisKey.REDIS_KEY_UPLOAD_CHUNKS, file.getUploadId()));
                if (chunks == null || chunks.size() != file.getTotalChunks())
                    throw new BusinessException("文件分片上传未完成，无法确认上传");
                for (int i = 0; i < file.getTotalChunks(); i++) {
                    File chunk = new File(file.getFilePath(), String.valueOf(i));
                    if (!chunk.isFile() || !chunk.canRead() || chunk.length() == 0)
                        throw new BusinessException("本机文件分片缺失或不可读取，请重新上传");
                }
            }
            projectService.confirmUploadAssessment(UploadConfirmDto.builder().projectId(projectId)
                    .assessmentId(assessment.getId()).taskId(assessment.getUploadTaskId())
                    .userId(UserContext.getUser().getId()).retryCount(0).files(files).build());
            return assessment.getId();
        });
    }

    @Override
    public void deleteTempFile(Long projectId, String uploadId, Long assessmentId) {
        Assessment assessment = currentAssessment(projectId, assessmentId);
        observe(assessment, "UPLOAD_CANCELLED", () -> {
            UploadFileDto file = file(assessment, uploadId);
            redisUtil.hdel(String.format(RedisKey.REDIS_KEY_FILE_UPLOAD_INFO, projectId), uploadId);
            redisUtil.setRemove(String.format(RedisKey.REDIS_KEY_FILE, projectId), file.getFileHash());
            FileUtils.delDirectory(file.getFilePath());
            if (currentFiles(assessment).isEmpty())
                publishFailure(assessment, "UPLOAD_CANCELLED", new IllegalStateException("User cancelled all uploaded files"));
            return null;
        });
    }

    private Assessment currentAssessment(Long projectId, Long expectedId) {
        Assessment assessment = projectService.requireUploadingAssessment(projectId);
        if (!Objects.equals(expectedId, assessment.getId()))
            throw new BusinessException(409, "请求属于旧评估，请刷新本次上传状态");
        return assessment;
    }

    private UploadFileDto file(Assessment assessment, String uploadId) {
        UploadFileDto file = (UploadFileDto) redisUtil.hget(
                String.format(RedisKey.REDIS_KEY_FILE_UPLOAD_INFO, assessment.getProjectId()), uploadId);
        if (file == null) throw new BusinessException("上传任务不存在或已过期");
        if (!Objects.equals(assessment.getId(), file.getAssessmentId()))
            throw new BusinessException(409, "文件不属于本次上传，请重新初始化上传");
        return file;
    }

    private List<UploadFileDto> currentFiles(Assessment assessment) {
        Map<Object, Object> metadata = redisUtil.hmget(
                String.format(RedisKey.REDIS_KEY_FILE_UPLOAD_INFO, assessment.getProjectId()));
        List<UploadFileDto> files = new ArrayList<>();
        if (metadata != null) for (Object value : metadata.values()) {
            UploadFileDto file = (UploadFileDto) value;
            if (Objects.equals(assessment.getId(), file.getAssessmentId())) files.add(file);
        }
        return files;
    }

    private <T> T observe(Assessment assessment, String stage, Supplier<T> operation) {
        try { return operation.get(); }
        catch (RuntimeException failure) {
            if (failure instanceof BusinessException && Integer.valueOf(409).equals(((BusinessException) failure).getCode()))
                throw failure;
            try { publishFailure(assessment, stage, failure); }
            catch (RuntimeException persistenceFailure) {
                failure.addSuppressed(persistenceFailure);
                log.error("评估失败状态暂时无法写入，超时任务将继续兜底 assessmentId={}", assessment.getId(), failure);
            }
            throw failure;
        }
    }

    private void publishFailure(Assessment assessment, String stage, Throwable failure) {
        StringWriter trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace));
        AssessmentCompletedEventMessage event = new AssessmentCompletedEventMessage(
                "assessment-" + assessment.getId() + "-" + stage + "-failed", String.valueOf(System.currentTimeMillis()),
                assessment.getUploadTaskId(), UserContext.getUser().getId(), assessment.getProjectId(), assessment.getId());
        event.setFailureStage(stage); event.setFailureDetail(trace.toString());
        log.error("上传评估异常 assessmentId={} stage={}", assessment.getId(), stage, failure);
        events.publishEvent(event);
    }
}
