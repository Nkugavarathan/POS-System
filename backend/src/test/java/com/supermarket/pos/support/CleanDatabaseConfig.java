package com.supermarket.pos.support;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Drops and re-creates the test schema when the (cached) test context starts. */
@TestConfiguration(proxyBeanMethods = false)
public class CleanDatabaseConfig {

    @Bean
    public FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
