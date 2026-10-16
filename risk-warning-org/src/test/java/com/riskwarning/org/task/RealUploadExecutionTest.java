package com.riskwarning.org.task;

import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.org.service.ProjectService;
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
        KafkaUtils kafka = mock(KafkaUtils.class);
        ExecuteQueueTask executor = new ExecuteQueueTask(mock(UploadTaskQueue.class),
                mock(DurableWorkStore.class), mock(RedisUtil.class), files, kafka);
        ProjectService projectService = mock(ProjectService.class);
        when(projectService.prepareUploadAssessment(18L, "deleted-project")).thenThrow(new IllegalStateException("Project deleted"));
        org.springframework.test.util.ReflectionTestUtils.setField(executor, "projectService", projectService);
        org.springframework.test.util.ReflectionTestUtils.setField(executor, "em", mock(javax.persistence.EntityManager.class));
        UploadConfirmDto task = UploadConfirmDto.builder().projectId(18L).taskId("deleted-project").build();
        assertThrows(IllegalStateException.class, () -> executor.execute(JSON.toJSONString(task)));
        verifyNoInteractions(files, kafka);
    }
    @Test void uploadCreatesPendingAssessmentAndSendsRealProcessingTask() throws Exception {
        Files.write(chunks.resolve("1"), "actual source".getBytes(StandardCharsets.UTF_8));
        FileRepository files = mock(FileRepository.class);
        KafkaUtils kafka = mock(KafkaUtils.class);
        ExecuteQueueTask executor = new ExecuteQueueTask(mock(UploadTaskQueue.class),
                mock(DurableWorkStore.class), mock(RedisUtil.class), files, kafka);
        ProjectService projectService = mock(ProjectService.class);
        when(projectService.prepareUploadAssessment(987654321L, "real-upload")).thenReturn(123L);
        org.springframework.test.util.ReflectionTestUtils.setField(executor, "projectService", projectService);
        javax.persistence.EntityManager em = mock(javax.persistence.EntityManager.class);
        when(em.find(eq(Assessment.class), eq(123L), eq(javax.persistence.LockModeType.PESSIMISTIC_WRITE)))
                .thenReturn(Assessment.builder().id(123L).status(AssessmentStatusEnum.TO_BE_ASSESSED).build());
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
            verify(projectService).prepareUploadAssessment(task.getProjectId(), task.getTaskId());
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

    @Test void mergeFailureStillHasAnAssessmentAndFinalizesWithItsId() {
        KafkaUtils kafka = mock(KafkaUtils.class);
        ProjectService projectService = mock(ProjectService.class);
        when(projectService.prepareUploadAssessment(18L, "missing-file")).thenReturn(123L);
        ExecuteQueueTask executor = new ExecuteQueueTask(mock(UploadTaskQueue.class), mock(DurableWorkStore.class),
                mock(RedisUtil.class), mock(FileRepository.class), kafka);
        javax.persistence.EntityManager em = mock(javax.persistence.EntityManager.class);
        when(em.find(eq(Assessment.class), eq(123L), eq(javax.persistence.LockModeType.PESSIMISTIC_WRITE)))
                .thenReturn(Assessment.builder().id(123L).status(AssessmentStatusEnum.TO_BE_ASSESSED).build());
        when(em.find(eq(com.riskwarning.common.po.project.Project.class), eq(18L), any(javax.persistence.LockModeType.class)))
                .thenReturn(new com.riskwarning.common.po.project.Project());
        when(em.find(com.riskwarning.common.po.project.Project.class, 18L)).thenReturn(new com.riskwarning.common.po.project.Project());
        org.springframework.test.util.ReflectionTestUtils.setField(executor, "em", em);
        org.springframework.test.util.ReflectionTestUtils.setField(executor, "projectService", projectService);
        String payload = JSON.toJSONString(UploadConfirmDto.builder().projectId(18L).userId(8L).taskId("missing-file").build());
        Throwable failure = assertThrows(IllegalStateException.class, () -> executor.execute(payload));
        executor.onExhausted(payload, failure);
        ArgumentCaptor<com.riskwarning.common.message.AssessmentCompletedEventMessage> event = ArgumentCaptor.forClass(com.riskwarning.common.message.AssessmentCompletedEventMessage.class);
        verify(projectService).onUploadFailure(event.capture());
        assertEquals(123L, event.getValue().getAssessmentId());
        assertEquals("FILE_MERGE", event.getValue().getFailureStage());
        assertTrue(event.getValue().getFailureDetail().contains("Upload metadata missing"));
    }
}
