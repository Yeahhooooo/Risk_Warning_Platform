package com.riskwarning.common.reliability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.io.PrintWriter;
import java.io.StringWriter;

/** PostgreSQL inbox/outbox. A database row lock owns execution until commit or rollback. */
@Slf4j
public class DurableWorkStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final String namespace;
    public DurableWorkStore(JdbcTemplate jdbc, TransactionTemplate transactions, String namespace) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.namespace = namespace;
    }
    public void initialize() {
        transactions.execute(status -> {
            String database = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                    connection -> connection.getMetaData().getDatabaseProductName());
            if("PostgreSQL".equals(database)) jdbc.execute("SELECT pg_advisory_xact_lock(7438291042)");
            createSchema();
            return null;
        });
    }
    private void createSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS assessment_work (id VARCHAR(36) PRIMARY KEY, "
                + "namespace VARCHAR(255) NOT NULL, kind VARCHAR(255) NOT NULL, task_key VARCHAR(255) NOT NULL, "
                + "payload TEXT NOT NULL, state VARCHAR(16) NOT NULL DEFAULT 'READY', attempts INTEGER NOT NULL DEFAULT 0, "
                + "last_error TEXT, available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS assessment_work_pending ON assessment_work(namespace, kind, state, available_at)");
    }
    public void enqueue(String kind, String key, String payload) {
        enqueueAt(kind, key, payload, Instant.now());
    }

    /** Persist delayed work in the caller's transaction, including restart recovery. */
    public void enqueueAt(String kind, String key, String payload, Instant availableAt) {
        if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("durable task requires a stable key");
        String id = UUID.nameUUIDFromBytes((namespace + "\n" + kind + "\n" + key).getBytes(StandardCharsets.UTF_8)).toString();
        jdbc.update("INSERT INTO assessment_work(id,namespace,kind,task_key,payload,available_at) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                id, namespace, kind, key, payload, Timestamp.from(availableAt));
    }
    @FunctionalInterface public interface Work { void execute(String payload) throws Exception; }
    @FunctionalInterface public interface FailureWork { void execute(String payload, Throwable failure) throws Exception; }

    /** maxAttempts=0 retries indefinitely (outbox). Failed business tasks retain their input. */
    public boolean runOne(String kind, Work work, int maxAttempts) {
        return runOne(kind, work, maxAttempts, (payload, failure) -> {});
    }

    public boolean runOne(String kind, Work work, int maxAttempts, FailureWork onExhausted) {
        String[] claimed = new String[1];
        // Retry finalization separately; never run the failed business operation again.
        if (finalizeOneFailure(kind, onExhausted)) return true;
        try {
            return Boolean.TRUE.equals(transactions.execute(status -> {
                List<Map<String,Object>> rows = jdbc.queryForList("SELECT id,payload FROM assessment_work "
                        + "WHERE namespace=? AND kind=? AND state='READY' AND available_at<=CURRENT_TIMESTAMP "
                        + "ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED", namespace, kind);
                if (rows.isEmpty()) return false;
                Map<String,Object> row = rows.get(0);
                claimed[0] = (String) row.get("id");
                log.info("[AssessmentFlow] stage=DURABLE_WORK status=START kind={} taskId={}", kind, claimed[0]);
                try { work.execute((String) row.get("payload")); }
                catch (Exception failure) { throw new IllegalStateException("Durable task failed", failure); }
                jdbc.update("UPDATE assessment_work SET state='DONE', updated_at=CURRENT_TIMESTAMP,last_error=NULL WHERE id=?", claimed[0]);
                return true;
            }));
        } catch (RuntimeException failure) {
            if (claimed[0] == null) throw failure;
            // Business changes have rolled back. Retain input and error in a separate transaction.
            transactions.execute(status -> {
                jdbc.update("UPDATE assessment_work SET attempts=attempts+1, "
                        + "state=CASE WHEN ?>0 AND attempts+1>=? THEN 'FAILURE_PENDING' ELSE 'READY' END, "
                        + "last_error=?,available_at=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND state='READY'",
                        maxAttempts, maxAttempts, stackTrace(failure),
                        Timestamp.from(Instant.now().plusSeconds(30)), claimed[0]);
                return null;
            });
            finalizeFailure(claimed[0], onExhausted, failure);
            log.error("[AssessmentFlow] stage=DURABLE_WORK status=FAILED kind={} taskId={} inputRetained=true", kind, claimed[0], failure);
            return true;
        }
    }

    private boolean finalizeOneFailure(String kind, FailureWork onExhausted) {
        String[] claimed = new String[1];
        try {
            return Boolean.TRUE.equals(transactions.execute(status -> {
                List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,payload,last_error FROM assessment_work "
                        + "WHERE namespace=? AND kind=? AND state='FAILURE_PENDING' AND available_at<=CURRENT_TIMESTAMP "
                        + "ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED", namespace, kind);
                if (rows.isEmpty()) return false;
                Map<String, Object> row = rows.get(0);
                claimed[0] = (String) row.get("id");
                finishFailure(row, onExhausted, new IllegalStateException((String) row.get("last_error")));
                return true;
            }));
        } catch (RuntimeException failure) {
            if (claimed[0] == null) throw failure;
            transactions.execute(status -> {
                jdbc.update("UPDATE assessment_work SET available_at=? WHERE id=? AND state='FAILURE_PENDING'",
                        Timestamp.from(Instant.now().plusSeconds(30)), claimed[0]);
                return null;
            });
            log.error("Failure finalization deferred; other work may proceed: taskId={}", claimed[0], failure);
            return false;
        }
    }

    private void finalizeFailure(String id, FailureWork onExhausted, Throwable failure) {
        try {
            transactions.execute(status -> {
                List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,payload,last_error FROM assessment_work "
                        + "WHERE id=? AND state='FAILURE_PENDING' FOR UPDATE", id);
                if (!rows.isEmpty()) finishFailure(rows.get(0), onExhausted, failure);
                return null;
            });
        } catch (RuntimeException e) {
            log.error("Failure finalization will be retried: taskId={}", id, e);
        }
    }

    private void finishFailure(Map<String, Object> row, FailureWork onExhausted, Throwable failure) {
        try { onExhausted.execute((String) row.get("payload"), failure); }
        catch (Exception e) { throw new IllegalStateException("Failure status update failed", e); }
        // Business failure status, notification outbox and terminal task state commit together.
        jdbc.update("UPDATE assessment_work SET state='FAILED',updated_at=CURRENT_TIMESTAMP WHERE id=?", row.get("id"));
    }

    private String stackTrace(Throwable failure) {
        StringWriter buffer = new StringWriter();
        failure.printStackTrace(new PrintWriter(buffer));
        return buffer.toString();
    }
}
