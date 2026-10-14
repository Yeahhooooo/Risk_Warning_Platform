package com.riskwarning.org.task;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.riskwarning.common.config.ElasticSearchConfig;
import com.riskwarning.common.constants.Constants;
import com.riskwarning.common.constants.RedisKey;
import com.riskwarning.common.reliability.DurableWorkHandler;
import com.riskwarning.common.utils.RedisUtil;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

/** Idempotent cleanup, executed by the existing durable worker after the deletion transaction commits. */
@Component
public class ProjectResourceCleanupTask implements DurableWorkHandler {
    private final ElasticsearchClient es;
    private final RedisUtil redis;
    private final Path workspace;
    @org.springframework.beans.factory.annotation.Autowired
    public ProjectResourceCleanupTask(ElasticsearchClient es, RedisUtil redis) {
        this(es, redis, Paths.get("").toAbsolutePath().normalize());
    }
    ProjectResourceCleanupTask(ElasticsearchClient es, RedisUtil redis, Path workspace) {
        this.es = es; this.redis = redis; this.workspace = workspace;
    }
    public String kind() { return "PROJECT_RESOURCE_CLEANUP"; }
    public int maxAttempts() { return 0; }

    public void execute(String payload) throws Exception {
        JSONObject task = JSON.parseObject(payload);
        Long id = task.getLong("projectId");
        if (id == null || id <= 0) throw new IllegalArgumentException("Invalid cleanup project ID");
        for (String index : new String[]{ElasticSearchConfig.BEHAVIOR_INDEX, ElasticSearchConfig.RISK_INDEX}) {
            DeleteByQueryResponse result = es.deleteByQuery(request -> request.index(index)
                    .allowNoIndices(true).ignoreUnavailable(true).refresh(true)
                    .query(query -> query.term(term -> term.field("project_id").value(id))));
            if (result.timedOut() || !result.failures().isEmpty() || result.versionConflicts() > 0)
                throw new IllegalStateException("Incomplete ES cleanup for project " + id + " index " + index);
        }
        redis.del(String.format(RedisKey.REDIS_KEY_FILE, id), String.format(RedisKey.REDIS_KEY_FILE_UPLOAD_INFO, id),
                String.format(RedisKey.REDIS_KEY_BEHAVIOR_PROCESSING_COUNT, id), String.format(RedisKey.REDIS_KEY_BEHAVIOR_PROCESSING_TOTAL, id));
        for (String directory : new String[]{Constants.FILE_TEMP_DIR, Constants.FILE_PERSIST_DIR,
                Constants.FILE_INTERNAL_DIR, Constants.FILE_PROCESSING_DIR}) {
            Path root = workspace.resolve(directory).toAbsolutePath().normalize();
            Path target = root.resolve(id.toString()).normalize();
            if (!target.startsWith(root) || target.equals(root)) throw new IllegalStateException("Invalid cleanup path");
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue;
            Path storage = workspace.toRealPath().resolve("storage");
            Path realRoot = root.toRealPath();
            if (!realRoot.startsWith(storage)) throw new IllegalStateException("Storage points outside the workspace");
            if (Files.isSymbolicLink(target)) {
                Files.delete(target);
                continue;
            }
            if (!target.toRealPath().startsWith(realRoot)) throw new IllegalStateException("Project directory points outside storage");
            // Files.walk does not follow symlinks. Only the selected project's directory is removed.
            try (Stream<Path> paths = Files.walk(target)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
            }
        }
    }
}
