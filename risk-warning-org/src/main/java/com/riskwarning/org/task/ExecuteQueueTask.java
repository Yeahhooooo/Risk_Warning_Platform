package com.riskwarning.org.task;

import com.riskwarning.common.constants.Constants;
import com.riskwarning.common.constants.RedisKey;
import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.common.enums.DataSourceTypeEnum;
import com.riskwarning.common.exception.BusinessException;
import com.riskwarning.common.message.BehaviorProcessingTaskMessage;
import com.riskwarning.common.po.file.ProjectFile;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.utils.FileUtils;
import com.riskwarning.common.utils.KafkaUtils;
import com.riskwarning.common.utils.RedisUtil;
import com.riskwarning.common.utils.StringUtils;
import com.riskwarning.org.entity.dto.UploadConfirmDto;
import com.riskwarning.org.entity.dto.UploadFileDto;
import com.riskwarning.org.repository.AssessmentRepository;
import com.riskwarning.org.repository.FileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private KafkaUtils kafkaUtils;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private UploadTaskQueue uploadTaskQueue;

    @PostConstruct
    public void consumeTransferQueue() {
        executorService.execute(() -> {
            log.info("[AssessmentFlow] stage=UPLOAD_QUEUE status=START queueKey={}", uploadTaskQueue.getQueueKey());
            while (true) {
                UploadConfirmDto uploadConfirmDto = uploadTaskQueue.poll();
                if(uploadConfirmDto == null) {
                    try {
                        Thread.sleep(1000); // 如果队列为空，等待1秒后再检查
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    continue;
                }
                log.info("[AssessmentFlow] stage=UPLOAD_QUEUE status=RECEIVED projectId={}", uploadConfirmDto.getProjectId());
                executorService.execute(() -> {
                    long started = System.nanoTime();
                    log.info("[AssessmentFlow] stage=FILE_MERGE status=START projectId={}", uploadConfirmDto.getProjectId());
                    transactionTemplate.execute(status -> {
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
                            log.info("[AssessmentFlow] stage=FILE_MERGE status=DONE projectId={} fileCount={} elapsedMs={}",
                                    uploadConfirmDto.getProjectId(), projectFile.getFilePaths().size(), (System.nanoTime() - started) / 1_000_000);


                            // todo: 创建Assessment实体
                            Assessment assessment = Assessment.builder()
                                    .projectId(uploadConfirmDto.getProjectId())
                                    .assessmentDate(null)
                                    .overallScore(null)
                                    .overallRiskLevel(null)
                                    .details(null)
                                    .status(AssessmentStatusEnum.TO_BE_ASSESSED)
                                    .createdAt(LocalDateTime.now())
                                    .build();

                            assessmentRepository.save(assessment);
                            // 先构造消息，但必须等当前数据库事务提交成功后再发送。
                            // 否则 processing 服务可能先消费消息，却查不到尚未提交的 Assessment。
                            BehaviorProcessingTaskMessage behaviorProcessingTaskMessage = new BehaviorProcessingTaskMessage(
                                    StringUtils.generateMessageId(),
                                    String.valueOf(System.currentTimeMillis()),
                                    StringUtils.generateTraceId(),
                                    uploadConfirmDto.getUserId(),
                                    uploadConfirmDto.getProjectId(),
                                    assessment.getId(),
                                    DataSourceTypeEnum.FILE_UPLOAD,
                                    projectFile.getFilePaths()
                            );
                            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    log.info("[AssessmentFlow] stage=ASSESSMENT_CREATE status=COMMITTED projectId={} assessmentId={}",
                                            behaviorProcessingTaskMessage.getProjectId(), behaviorProcessingTaskMessage.getAssessmentId());
                                    kafkaUtils.sendMessage(behaviorProcessingTaskMessage);
                                    log.info("数据库事务已提交，评估任务消息已提交发送，broker确认请查看KAFKA_SEND ACK，projectId={}, assessmentId={}",
                                            behaviorProcessingTaskMessage.getProjectId(),
                                            behaviorProcessingTaskMessage.getAssessmentId());
                                }
                            });
                            return true;
                        } catch (Exception e) {
                            log.error("[AssessmentFlow] stage=UPLOAD_PREPARE status=FAILED projectId={} elapsedMs={}",
                                    uploadConfirmDto.getProjectId(), (System.nanoTime() - started) / 1_000_000, e);
                            log.error("Error processing file upload confirm queue", e);
                            if(uploadConfirmDto != null && uploadConfirmDto.getRetryCount() >= Constants.UPLOAD_CONFIRM_RETRY_LIMIT) {
                                throw new BusinessException("项目" + uploadConfirmDto.getProjectId() + "文件确认失败，重试次数已达上限");
                            }
                            return false;
                        } finally {
                            // 删除缓存
                            redisUtil.del(String.format(RedisKey.REDIS_KEY_FILE, uploadConfirmDto.getProjectId()));
                            redisUtil.del(String.format(RedisKey.REDIS_KEY_FILE_UPLOAD_INFO, uploadConfirmDto.getProjectId()));
                            FileUtils.delDirectory(Constants.getTempFileDirPath(uploadConfirmDto.getProjectId(), ""));
                        }
                    });
                });
            }
        });

    }

}
