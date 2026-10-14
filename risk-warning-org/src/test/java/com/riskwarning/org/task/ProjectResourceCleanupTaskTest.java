package com.riskwarning.org.task;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.*;
import com.riskwarning.common.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectResourceCleanupTaskTest {
    private final ElasticsearchClient es = mock(ElasticsearchClient.class);
    private final RedisUtil redis = mock(RedisUtil.class);
    @TempDir Path workspace;
    private ProjectResourceCleanupTask cleanup;
    @BeforeEach void setup() { cleanup = new ProjectResourceCleanupTask(es,redis,workspace); }
    @Test void incompleteEsDeletionRetainsOtherResourcesAndRetries() throws Exception {
        DeleteByQueryResponse response = new DeleteByQueryResponse.Builder().timedOut(true).versionConflicts(0L).build();
        when(es.deleteByQuery(any(DeleteByQueryRequest.class))).thenReturn(response);
        assertThrows(IllegalStateException.class,()->cleanup.execute("{\"projectId\":18}"));
        ArgumentCaptor<DeleteByQueryRequest> query = ArgumentCaptor.forClass(DeleteByQueryRequest.class);
        verify(es).deleteByQuery(query.capture());
        assertEquals("project_id",query.getValue().query().term().field());
        assertEquals(18L,query.getValue().query().term().value().longValue());
        assertEquals("t_behavior",query.getValue().index().get(0));
        verifyNoInteractions(redis);
        assertEquals(0,cleanup.maxAttempts());
    }
    @Test void invalidProjectIdCannotDeleteAnyResources() {
        assertThrows(IllegalArgumentException.class,()->cleanup.execute("{\"projectId\":-1}"));
        verifyNoInteractions(es,redis);
    }
    @Test void completedCleanupIsScopedAndRepeatable() throws Exception {
        DeleteByQueryResponse response = new DeleteByQueryResponse.Builder().timedOut(false).versionConflicts(0L).build();
        when(es.deleteByQuery(any(DeleteByQueryRequest.class))).thenReturn(response);
        Path target = workspace.resolve("storage/persist/987654322");
        Path other = workspace.resolve("storage/persist/987654323");
        Files.createDirectories(target); Files.createDirectories(other);
        Files.write(target.resolve("source.txt"),new byte[]{1});
        Files.write(other.resolve("source.txt"),new byte[]{2});
        cleanup.execute("{\"projectId\":987654322}");
        assertFalse(Files.exists(target)); assertTrue(Files.exists(other.resolve("source.txt")));
        cleanup.execute("{\"projectId\":987654322}");
        ArgumentCaptor<DeleteByQueryRequest> query = ArgumentCaptor.forClass(DeleteByQueryRequest.class);
        verify(es,times(4)).deleteByQuery(query.capture());
        assertEquals("t_risk",query.getAllValues().get(1).index().get(0));
        for (DeleteByQueryRequest request : query.getAllValues()) {
            assertEquals(987654322L,request.query().term().value().longValue());
            assertTrue(request.refresh());
        }
        verify(redis,times(2)).del("project:987654322:file:hash", "project:987654322:file:chunk",
                "project:987654322:behavior:processing:count", "project:987654322:behavior:processing:total");
    }
}
