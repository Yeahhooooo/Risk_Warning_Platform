package com.riskwarning.org.task;

import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.reliability.DurableWorkStore;
import com.riskwarning.common.utils.*;
import com.riskwarning.org.entity.dto.*;
import com.riskwarning.org.repository.*;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadHandoffTest {
    @Test void failedDatabaseHandoffDoesNotAcknowledgeRedisTask() {
        UploadTaskQueue queue = mock(UploadTaskQueue.class);
        DurableWorkStore store = mock(DurableWorkStore.class);
        UploadConfirmDto task = UploadConfirmDto.builder().projectId(1L).taskId("task-1").files(Collections.emptyList()).build();
        when(queue.poll()).thenReturn(task);
        when(queue.getQueueKey()).thenReturn("isolated-queue");
        doThrow(new IllegalStateException("database unavailable")).when(store).enqueue(anyString(), anyString(), anyString());
        ExecuteQueueTask bridge = new ExecuteQueueTask(queue, store, mock(RedisUtil.class), mock(FileRepository.class), mock(AssessmentRepository.class), mock(KafkaUtils.class));
        assertThrows(IllegalStateException.class, bridge::handoffOne);
        verify(queue, never()).acknowledge(any());
    }
    @Test void legacyHandoffKeepsStableIdentityAndDoesNotMutateRedisValue() {
        UploadTaskQueue queue = mock(UploadTaskQueue.class);
        DurableWorkStore store = mock(DurableWorkStore.class);
        UploadConfirmDto task = UploadConfirmDto.builder().projectId(1L).userId(2L).build();
        when(queue.poll()).thenReturn(task);
        when(queue.getQueueKey()).thenReturn("isolated-queue");
        RedisUtil redis = mock(RedisUtil.class);
        UploadFileDto file = UploadFileDto.builder().uploadId("file-1").filePath("chunks").build();
        when(redis.hmget(anyString())).thenReturn(Collections.singletonMap("file-1",file));
        java.util.List<String> ids = new java.util.ArrayList<>();
        doAnswer(call -> {
            ids.add(call.getArgument(1));
            UploadConfirmDto stored = JSON.parseObject((String)call.getArgument(2), UploadConfirmDto.class);
            assertEquals("chunks", stored.getFiles().get(0).getFilePath());
            return null;
        }).when(store).enqueue(anyString(), anyString(), anyString());
        ExecuteQueueTask bridge = new ExecuteQueueTask(queue, store, redis, mock(FileRepository.class), mock(AssessmentRepository.class), mock(KafkaUtils.class));
        bridge.handoffOne(); bridge.handoffOne();
        assertEquals(ids.get(0),ids.get(1));
        assertNull(task.getTaskId()); assertNull(task.getFiles());
    }

    /** The upload handoff must always reach the real executor. */
    @Test void handoffKindMatchesRealExecutorClaim() {
        UploadTaskQueue queue = mock(UploadTaskQueue.class);
        DurableWorkStore store = mock(DurableWorkStore.class);
        UploadConfirmDto task = UploadConfirmDto.builder().projectId(1L).taskId("task-1").build();
        when(queue.poll()).thenReturn(task);
        when(queue.getQueueKey()).thenReturn("isolated-queue");

        List<String> writtenKinds = new java.util.ArrayList<>();
        doAnswer(call -> {
            writtenKinds.add(call.getArgument(0));
            return null;
        }).when(store).enqueue(anyString(), anyString(), anyString());

        ExecuteQueueTask bridge = new ExecuteQueueTask(queue, store, mock(RedisUtil.class),
                mock(FileRepository.class), mock(AssessmentRepository.class), mock(KafkaUtils.class));
        bridge.handoffOne();

        assertEquals(Collections.singletonList("UPLOAD_INBOX"), writtenKinds,
                "handoff 必须写入 UPLOAD_INBOX");
        assertEquals("UPLOAD_INBOX", bridge.kind(),
                "真实执行器必须认领上传工作项");
    }
}
