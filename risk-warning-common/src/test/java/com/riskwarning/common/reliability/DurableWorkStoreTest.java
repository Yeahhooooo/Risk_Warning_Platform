package com.riskwarning.common.reliability;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class DurableWorkStoreTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    DurableWorkStore store;
    @BeforeEach void setup() {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        store = new DurableWorkStore(jdbc, tx, "test");
        store.initialize();
        jdbc.execute("create table business_result(id integer primary key)");
    }
    @Test void rolledBackBusinessMustNotPublishOutbox() {
        assertThrows(IllegalStateException.class, () -> tx.execute(s -> {
            store.enqueue("OUTBOX", "event-1", "payload");
            throw new IllegalStateException("business rollback");
        }));
        assertEquals(0, jdbc.queryForObject("select count(*) from assessment_work", Integer.class));
    }
    @Test void duplicateDeliveryAndRestartExecuteOnce() {
        store.enqueue("INBOX", "event-1", "payload");
        store.enqueue("INBOX", "event-1", "payload");
        DurableWorkStore restarted = new DurableWorkStore(jdbc, tx, "test");
        assertTrue(restarted.runOne("INBOX", p -> jdbc.update("insert into business_result values (1)"), 3));
        assertFalse(restarted.runOne("INBOX", p -> fail("duplicate execution"), 3));
        assertEquals(1, jdbc.queryForObject("select count(*) from business_result", Integer.class));
    }
    @Test void failedExecutionRollsBackAndRetainsPayload() {
        store.enqueue("INBOX", "event-1", "payload");
        store.runOne("INBOX", p -> {
            jdbc.update("insert into business_result values (1)");
            throw new IllegalStateException("injected failure");
        }, 1);
        assertEquals(0, jdbc.queryForObject("select count(*) from business_result", Integer.class));
        assertEquals("FAILED", jdbc.queryForObject("select state from assessment_work", String.class));
        assertEquals("payload", jdbc.queryForObject("select payload from assessment_work", String.class));
        assertTrue(jdbc.queryForObject("select last_error from assessment_work", String.class).contains("injected failure"));
    }
    @Test void twoWorkersCannotExecuteSameLockedTask() throws Exception {
        store.enqueue("INBOX", "event-1", "payload");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> first = executor.submit(() -> store.runOne("INBOX", p -> {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            }, 3));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertFalse(store.runOne("INBOX", p -> fail("concurrent execution"), 3));
            release.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS));
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test void failedOutboxCanBeRetriedAfterRestart() {
        store.enqueue("OUTBOX", "event-1", "payload");
        store.runOne("OUTBOX", p -> {throw new IllegalStateException("broker unavailable");}, 0);
        assertEquals("READY", jdbc.queryForObject("select state from assessment_work", String.class));
        jdbc.update("update assessment_work set available_at = CURRENT_TIMESTAMP");
        new DurableWorkStore(jdbc, tx, "test").runOne("OUTBOX", p -> {}, 0);
        assertEquals("DONE", jdbc.queryForObject("select state from assessment_work", String.class));
    }
    @Test void exhaustedTaskInvokesFailureStatusAfterBusinessRollback() {
        store.enqueue("INBOX", "event-1", "payload");
        store.runOne("INBOX", p -> {
            jdbc.update("insert into business_result values (1)");
            throw new IllegalStateException("failure");
        }, 1, p -> {
            assertEquals(0, jdbc.queryForObject("select count(*) from business_result", Integer.class));
            jdbc.update("insert into business_result values (2)");
        });
        assertEquals(2, jdbc.queryForObject("select id from business_result", Integer.class));
        assertEquals("FAILED", jdbc.queryForObject("select state from assessment_work", String.class));
    }
}
