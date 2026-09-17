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
        if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("durable task requires a stable key");
        String id = UUID.nameUUIDFromBytes((namespace + "\n" + kind + "\n" + key).getBytes(StandardCharsets.UTF_8)).toString();
        jdbc.update("INSERT INTO assessment_work(id,namespace,kind,task_key,payload) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",
                id, namespace, kind, key, payload);
    }
    @FunctionalInterface public interface Work { void execute(String payload) throws Exception; }

    /** maxAttempts=0 retries indefinitely (outbox). Failed business tasks retain their input. */
    public boolean runOne(String kind, Work work, int maxAttempts) {
        return runOne(kind, work, maxAttempts, payload -> {});
    }

    public boolean runOne(String kind, Work work, int maxAttempts, Work onExhausted) {
        String[] claimed = new String[1];
        String[] input = new String[1];
        try {
            return Boolean.TRUE.equals(transactions.execute(status -> {
                List<Map<String,Object>> rows = jdbc.queryForList("SELECT id,payload FROM assessment_work "
                        + "WHERE namespace=? AND kind=? AND state='READY' AND available_at<=CURRENT_TIMESTAMP "
                        + "ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED", namespace, kind);
                if (rows.isEmpty()) return false;
                Map<String,Object> row = rows.get(0);
                claimed[0] = (String) row.get("id");
                input[0] = (String) row.get("payload");
                log.info("[AssessmentFlow] stage=DURABLE_WORK status=START kind={} taskId={}", kind, claimed[0]);
                try { work.execute((String) row.get("payload")); }
                catch (Exception failure) { throw new IllegalStateException("Durable task failed", failure); }
                jdbc.update("UPDATE assessment_work SET state='DONE', updated_at=CURRENT_TIMESTAMP,last_error=NULL WHERE id=?", claimed[0]);
                return true;
            }));
        } catch (RuntimeException failure) {
            if (claimed[0] == null) throw failure;
            // Business changes have rolled back. Retain input and error in a separate transaction.
            Boolean exhausted = transactions.execute(status -> {
                jdbc.update("UPDATE assessment_work SET attempts=attempts+1, "
                        + "state=CASE WHEN ?>0 AND attempts+1>=? THEN 'FAILED' ELSE 'READY' END, "
                        + "last_error=?,available_at=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND state='READY'",
                        maxAttempts, maxAttempts, failure.toString() + ": " + failure.getCause(),
                        Timestamp.from(Instant.now().plusSeconds(30)), claimed[0]);
                return "FAILED".equals(jdbc.queryForObject("SELECT state FROM assessment_work WHERE id=?", String.class, claimed[0]));
            });
            if(Boolean.TRUE.equals(exhausted)) {
                try {
                    transactions.execute(status -> {
                        try { onExhausted.execute(input[0]); }
                        catch(Exception e) { throw new IllegalStateException("Failure status update failed", e); }
                        return null;
                    });
                } catch(RuntimeException e) { log.error("Task remains FAILED, but business status could not be updated: {}", claimed[0], e); }
            }
            log.error("[AssessmentFlow] stage=DURABLE_WORK status=FAILED kind={} taskId={} inputRetained=true", kind, claimed[0], failure);
            return true;
        }
    }
}
