package com.riskwarning.common.reliability;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import javax.persistence.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JpaDurableWorkTest {
    @Entity(name="WorkProbe") @Table(name="work_probe")
    public static class Probe { @Id public Long id; public Probe() {} }

    @Test void businessEntityOutboxAndCompletionShareJpaTransaction() {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);
        factory.setPackagesToScan("com.riskwarning.common.reliability");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Collections.singletonMap("hibernate.hbm2ddl.auto", "create-drop"));
        factory.afterPropertiesSet();
        try {
            EntityManagerFactory emf = factory.getObject();
            EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(emf);
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            DurableWorkStore store = new DurableWorkStore(jdbc, new TransactionTemplate(new JpaTransactionManager(emf)), "jpa");
            store.initialize();
            store.enqueue("INBOX", "one", "payload");
            store.runOne("INBOX", p -> {
                Probe entity = new Probe(); entity.id = 1L; em.persist(entity); em.flush();
                store.enqueue("OUTBOX", "event", "payload");
                throw new IllegalStateException("failure after flush");
            }, 3);
            assertEquals(0, jdbc.queryForObject("select count(*) from work_probe", Integer.class));
            assertEquals(0, jdbc.queryForObject("select count(*) from assessment_work where kind='OUTBOX'", Integer.class));
            jdbc.update("update assessment_work set available_at=CURRENT_TIMESTAMP");
            store.runOne("INBOX", p -> {
                Probe entity = new Probe(); entity.id = 1L; em.persist(entity);
                store.enqueue("OUTBOX", "event", "payload");
            }, 3);
            assertEquals(1, jdbc.queryForObject("select count(*) from work_probe", Integer.class));
            assertEquals("DONE", jdbc.queryForObject("select state from assessment_work where kind='INBOX'", String.class));
            assertEquals("READY", jdbc.queryForObject("select state from assessment_work where kind='OUTBOX'", String.class));
        } finally { factory.destroy(); }
    }
}
