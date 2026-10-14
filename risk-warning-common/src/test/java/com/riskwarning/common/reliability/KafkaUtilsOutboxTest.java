package com.riskwarning.common.reliability;

import com.riskwarning.common.message.IndicatorCalculationTaskMessage;
import com.riskwarning.common.utils.KafkaUtils;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KafkaUtilsOutboxTest {
    @Test void sendJoinsBusinessTransactionInsteadOfPublishingBeforeCommit() {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        DurableWorkStore store = new DurableWorkStore(jdbc, tx, "test");
        store.initialize();
        KafkaUtils utils = new KafkaUtils();
        KafkaTemplate kafka = mock(KafkaTemplate.class);
        ReflectionTestUtils.setField(utils, "kafkaTemplate", kafka);
        ReflectionTestUtils.setField(utils, "durableWorkStore", store);
        IndicatorCalculationTaskMessage message = new IndicatorCalculationTaskMessage("event", "now", "trace", 1L, 2L, 3L);
        tx.execute(status -> { utils.sendMessage(message); status.setRollbackOnly(); return null; });
        assertEquals(0, jdbc.queryForObject("select count(*) from assessment_work", Integer.class));
        tx.execute(status -> { utils.sendMessage(message); return null; });
        assertEquals("KAFKA_OUTBOX", jdbc.queryForObject("select kind from assessment_work", String.class));
        verifyNoInteractions(kafka);
    }
}
