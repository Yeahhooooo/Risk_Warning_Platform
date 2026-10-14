package com.riskwarning.org.task;

import com.riskwarning.common.exception.BusinessException;
import com.riskwarning.common.utils.RedisUtil;
import com.riskwarning.org.entity.dto.UploadConfirmDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadTaskQueueTest {
    private final RedisUtil redis = mock(RedisUtil.class);
    private final String key = "file:confirmed:queue:developer-a";

    @Test
    void publisherAndConsumerUseTheSameIsolatedKey() {
        UploadTaskQueue queue = new UploadTaskQueue(redis, key);
        UploadConfirmDto task = UploadConfirmDto.builder().projectId(255L).userId(19L).retryCount(0).build();
        when(redis.lSet(key, task)).thenReturn(true);
        when(redis.claimListItem(key, key + ":processing")).thenReturn(task);

        queue.enqueue(task);
        assertSame(task, queue.poll());
        verify(redis).lSet(key, task);
        verify(redis).claimListItem(key, key + ":processing");
        queue.acknowledge(task);
        verify(redis).acknowledgeListItem(key + ":processing", task);
        verify(redis, never()).rightPop("file:confirmed:queue");
        verifyNoMoreInteractions(redis);
    }

    @Test
    void failedEnqueueMustNotBeReportedAsSuccessfulConfirmation() {
        UploadTaskQueue queue = new UploadTaskQueue(redis, key);
        UploadConfirmDto task = UploadConfirmDto.builder().projectId(255L).build();
        when(redis.lSet(key, task)).thenReturn(false);
        assertThrows(BusinessException.class, () -> queue.enqueue(task));
    }

    @Test
    void emptyQueueReturnsNull() {
        assertNull(new UploadTaskQueue(redis, key).poll());
    }
}
