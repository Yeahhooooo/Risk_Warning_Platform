package com.riskwarning.common.reliability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.SmartLifecycle;
import java.util.List;
import java.util.concurrent.*;

@Configuration
@ConditionalOnProperty(name="assessment.reliability.enabled", havingValue="true")
@Slf4j
public class ReliabilityConfiguration {
    @Bean
    public org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<Object,Object> durableKafkaListenerContainerFactory(
            org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            org.springframework.kafka.core.ConsumerFactory<Object,Object> consumerFactory) {
        org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<Object,Object> factory =
                new org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        java.util.Properties consumerProperties = new java.util.Properties();
        consumerProperties.put("enable.auto.commit", "false");
        factory.getContainerProperties().setKafkaConsumerProperties(consumerProperties);
        factory.getContainerProperties().setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
        org.springframework.kafka.listener.DefaultErrorHandler errors = new org.springframework.kafka.listener.DefaultErrorHandler(
                new org.springframework.util.backoff.FixedBackOff(5000L, Long.MAX_VALUE));
        errors.setClassifications(java.util.Collections.singletonMap(Exception.class, true), true);
        factory.setCommonErrorHandler(errors);
        return factory;
    }
    @Bean
    public DurableWorkStore durableWorkStore(JdbcTemplate jdbc, PlatformTransactionManager manager,
            @Value("${assessment.reliability.namespace:${spring.application.name}}") String namespace) {
        TransactionTemplate tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        DurableWorkStore store = new DurableWorkStore(jdbc, tx, namespace);
        store.initialize();
        return store;
    }
    @Bean
    public SmartLifecycle durableWorkers(DurableWorkStore store, List<DurableWorkHandler> handlers) {
        return new SmartLifecycle() {
            volatile boolean running;
            ScheduledExecutorService pool;
            public void start() {
                pool = Executors.newScheduledThreadPool(Math.max(1, handlers.size()), r -> {
                    Thread thread = new Thread(r, "assessment-durable-worker"); thread.setDaemon(true); return thread;
                });
                running = true;
                for (DurableWorkHandler handler : handlers) pool.scheduleWithFixedDelay(() -> {
                    try { store.runOne(handler.kind(), handler::execute, handler.maxAttempts(), handler::onExhausted); }
                    catch (Exception failure) { log.error("Durable worker polling failed; will retry, kind={}", handler.kind(), failure); }
                }, 1, 2, TimeUnit.SECONDS);
            }
            public void stop() { running = false; if(pool != null) pool.shutdownNow(); }
            public boolean isRunning() { return running; }
        };
    }
}
