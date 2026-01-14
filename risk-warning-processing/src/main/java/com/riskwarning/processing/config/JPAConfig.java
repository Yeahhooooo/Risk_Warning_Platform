package com.riskwarning.processing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.persistence.EntityManagerFactory;

@Configuration
public class JPAConfig {

    @Bean
    @Primary
    public PlatformTransactionManager jpaTransactionManager(
            EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
