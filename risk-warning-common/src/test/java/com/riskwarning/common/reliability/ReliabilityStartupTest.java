package com.riskwarning.common.reliability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ReliabilityStartupTest {
    @Test void serviceConfigurationsRegisterRequiredDurableBeans() throws Exception {
        for (String service : new String[]{"org", "processing", "report"}) {
            DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                    + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
            org.springframework.core.env.EnumerablePropertySource<?> properties = (org.springframework.core.env.EnumerablePropertySource<?>) new YamlPropertySourceLoader().load(service,
                    new FileSystemResource("../risk-warning-" + service + "/src/main/resources/application.yml")).get(0);
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
                    .withUserConfiguration(ReliabilityConfiguration.class)
                    .withPropertyValues(java.util.Arrays.stream(properties.getPropertyNames())
                            .map(name -> name + "=" + properties.getProperty(name)).toArray(String[]::new))
                    .withPropertyValues("spring.application.name=risk-warning-" + service)
                    .withBean(JdbcTemplate.class, () -> new JdbcTemplate(ds))
                    .withBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(ds))
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(DurableWorkStore.class);
                        assertThat(context).hasBean("durableKafkaListenerContainerFactory");
                        context.getBean(DurableWorkStore.class).enqueue("PROBE", "startup", "{}");
                        assertThat(new JdbcTemplate(ds).queryForObject("select count(*) from assessment_work", Integer.class)).isEqualTo(1);
                    });
        }
    }
}
