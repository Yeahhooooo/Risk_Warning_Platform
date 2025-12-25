package com.riskwarning.org.task;

import com.riskwarning.common.constants.Constants;
import com.riskwarning.common.constants.RedisKey;
import com.riskwarning.common.exception.BusinessException;
import com.riskwarning.common.po.file.ProjectFile;
import com.riskwarning.common.utils.FileUtils;
import com.riskwarning.common.utils.RedisUtil;
import com.riskwarning.common.utils.StringUtils;
import com.riskwarning.org.entity.dto.UploadConfirmDto;
import com.riskwarning.org.entity.dto.UploadFileDto;
import com.riskwarning.org.repository.FileRepository;
import com.riskwarning.org.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Slf4j
public class ExecuteQueueTask {

    private ExecutorService executorService = Executors.newFixedThreadPool(10);


    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private FileRepository fileRepository;

    @PostConstruct
    public void consumeTransferQueue() {
        executorService.execute(() -> {
            while (true) {
                UploadConfirmDto uploadConfirmDto = (UploadConfirmDto) redisUtil.rightPop(RedisKey.REDIS_KEY_CONFIRMED_FILE_QUEUE);
                if(uploadConfirmDto == null) {
                    try {
                        Thread.sleep(1000); // 如果队列为空，等待1秒后再检查
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    continue;
                }
                executorService.execute(() -> {
                    try {
                        Map<Object, Object> uploadFileMap = redisUtil.hmget(String.format(RedisKey.REDIS_KEY_FILE_UPLOAD_INFO, uploadConfirmDto.getProjectId()));
                        ProjectFile projectFile = ProjectFile.builder()
                                .projectId(uploadConfirmDto.getProjectId())
                                .filePaths(new ArrayList<>())
                                .build();
                        for(Object value : uploadFileMap.values()) {
                            UploadFileDto uploadFileDto = (UploadFileDto) value;
                            // todo: 文件需要保存到远程存储，这里只是本地合并，后续需要改造
                            String targetFilePath = Constants.getPersistFileDirPath(uploadFileDto.getProjectId())
                                    + StringUtils.generateFileName(uploadFileDto.getProjectId(), uploadFileDto.getUploadId()) + "." + uploadFileDto.getFileSuffix();
                            FileUtils.union(
                                    uploadFileDto.getFilePath(),
                                    targetFilePath,
                                    true
                            );

                            projectFile.setUserId(uploadFileDto.getUserId());
                            projectFile.getFilePaths().add(targetFilePath);
                        }
                        fileRepository.save(projectFile);
                        // 成功后删除缓存
                        redisUtil.del(String.format(RedisKey.REDIS_KEY_FILE, uploadConfirmDto.getProjectId()));
                        redisUtil.del(String.format(RedisKey.REDIS_KEY_FILE_UPLOAD_INFO, uploadConfirmDto.getProjectId()));

                        // todo: 发送消息队列

                    } catch (Exception e) {
                        log.error("Error processing file upload confirm queue", e);
                        if(uploadConfirmDto != null && uploadConfirmDto.getRetryCount() >= Constants.UPLOAD_CONFIRM_RETRY_LIMIT) {
                            throw new BusinessException("项目" + uploadConfirmDto.getProjectId() + "文件确认失败，重试次数已达上限");
                        }
                        FileUtils.delDirectory(Constants.getPersistFileDirPath(uploadConfirmDto.getProjectId()));
                    }
                });
            }
        });

    }

}
