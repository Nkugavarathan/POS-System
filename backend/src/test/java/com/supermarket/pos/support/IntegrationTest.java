package com.supermarket.pos.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Full Spring context against the local test database (TEST_DB_URL). Classes using it must
 * be named *IT so they run in the Failsafe phase: {@code mvnw verify}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import(CleanDatabaseConfig.class)
public @interface IntegrationTest {
}
