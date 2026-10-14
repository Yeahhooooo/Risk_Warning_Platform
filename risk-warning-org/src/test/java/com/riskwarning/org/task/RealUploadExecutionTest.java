package com.riskwarning.org.task;

import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.common.message.BehaviorProcessingTaskMessage;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.reliability.DurableWorkStore;
import com.riskwarning.common.utils.*;
import com.riskwarning.org.entity.dto.*;
import com.riskwarning.org.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealUploadExecutionTest {
    @TempDir Path chunks;
    @Test void queuedUploadCannotRecreateDeletedProject() {
        FileRepository files = mock(FileRepository.class);
        AssessmentRepository assessments = mock(AssessmentRepository.class);
        KafkaUtils kafka = mock(KafkaUtils.class);
        ExecuteQueueTask executor = new ExecuteQueueTask(mock(UploadTaskQueue.class),
                mock(DurableWorkStore.class), mock(RedisUtil.class), files, assessments, kafka);
        org.springframework.test.util.ReflectionTestUtils.setField(executor, "em", mock(javax.persistence.EntityManager.class));
        UploadConfirmDto task = UploadConfirmDto.builder().projectId(18L).taskId("deleted-project").build();
        assertThrows(IllegalStateException.class, () -> executor.execute(JSON.toJSONString(task)));
        verifyNoInteractions(files, assessments, kafka);
    }
    @Test void uploadCreatesPendingAssessmentAndSendsRealProcessingTask() throws Exception {
        Files.write(chunks.resolve("1"), "actual source".getBytes(StandardCharsets.UTF_8));
        AssessmentRepository assessments = mock(AssessmentRepository.class);
        FileRepository files = mock(FileRepository.class);
        KafkaUtils kafka = mock(KafkaUtils.class);
        when(assessments.save(any(Assessment.class))).thenAnswer(call -> {
            Assessment row = call.getArgument(0); row.setId(123L); return row;
        });
        ExecuteQueueTask executor = new ExecuteQueueTask(mock(UploadTaskQueue.class),
                mock(DurableWorkStore.class), mock(RedisUtil.class), files, assessments, kafka);
        javax.persistence.EntityManager em = mock(javax.persistence.EntityManager.class);
        when(em.find(eq(com.riskwarning.common.po.project.Project.class), anyLong(),
                eq(javax.persistence.LockModeType.PESSIMISTIC_READ)))
                .thenReturn(new com.riskwarning.common.po.project.Project());
        org.springframework.test.util.ReflectionTestUtils.setField(executor, "em", em);
        UploadFileDto file = UploadFileDto.builder().uploadId(UUID.randomUUID().toString())
                .filePath(chunks.toString()).fileSuffix("txt").build();
        UploadConfirmDto task = UploadConfirmDto.builder().projectId(987654321L).userId(8L)
                .taskId("real-upload").files(Collections.singletonList(file)).build();
        Path output = null;
        try {
            executor.execute(JSON.toJSONString(task));
            ArgumentCaptor<Assessment> row = ArgumentCaptor.forClass(Assessment.class);
            verify(assessments).save(row.capture());
            assertEquals(AssessmentStatusEnum.TO_BE_ASSESSED, row.getValue().getStatus());
            assertNull(row.getValue().getOverallScore());
            assertNull(row.getValue().getDetails());
            ArgumentCaptor<BehaviorProcessingTaskMessage> message = ArgumentCaptor.forClass(BehaviorProcessingTaskMessage.class);
            verify(kafka).sendMessage(message.capture());
            assertEquals(123L, message.getValue().getAssessmentId());
            assertEquals(task.getProjectId(), message.getValue().getProjectId());
            output = Paths.get(message.getValue().getFilePaths().get(0));
            assertEquals(Paths.get(com.riskwarning.common.constants.Constants.getPersistFileDirPath(task.getProjectId())),
                    output.getParent());
            assertEquals("actual source", new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
            assertTrue(Files.exists(chunks.resolve("1")), "Source input must remain available for retry");
        } finally {
            if (output != null) Files.deleteIfExists(output);
        }
    }
}
