package com.riskwarning.processing.task;

import com.riskwarning.common.message.IndicatorCalculationTaskMessage;
import com.riskwarning.common.reliability.*;
import com.riskwarning.common.utils.KafkaUtils;
import com.riskwarning.processing.batch.BatchJob;
import com.riskwarning.processing.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessingInboxTest {
    @Test void listenerPersistsBeforeReturningAndWorkerResumesAfterRestart() {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        DurableWorkStore store = new DurableWorkStore(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)), "processing-test");
        store.initialize();
        BehaviorProcessingService behaviors = mock(BehaviorProcessingService.class);
        MessageTask listener = new MessageTask(store, mock(BatchJob.class), mock(DocumentProcessingService.class), behaviors, mock(KafkaUtils.class));
        IndicatorCalculationTaskMessage message = new IndicatorCalculationTaskMessage("event-1", "now", "trace", 2L, 257L, 339L);
        listener.onMessage(message); listener.onMessage(message);
        assertEquals(1, jdbc.queryForObject("select count(*) from assessment_work", Integer.class));
        verifyNoInteractions(behaviors);
        MessageTask restarted = new MessageTask(store, mock(BatchJob.class), mock(DocumentProcessingService.class), behaviors, mock(KafkaUtils.class));
        DurableWorkHandler handler = restarted.indicatorInboxHandler();
        store.runOne(handler.kind(), handler::execute, handler.maxAttempts());
        assertEquals("DONE", jdbc.queryForObject("select state from assessment_work", String.class));
        verify(behaviors).processProjectBehaviors(2L,257L,339L);
    }
    @Test void databaseFailureEscapesListenerForKafkaRetry() {
        DurableWorkStore store = mock(DurableWorkStore.class);
        doThrow(new IllegalStateException("database down")).when(store).enqueue(anyString(), anyString(), anyString());
        MessageTask listener = new MessageTask(store, mock(BatchJob.class), mock(DocumentProcessingService.class), mock(BehaviorProcessingService.class), mock(KafkaUtils.class));
        assertThrows(IllegalStateException.class, () -> listener.onMessage(new IndicatorCalculationTaskMessage("event-1","now","trace",2L,257L,339L)));
    }
}
