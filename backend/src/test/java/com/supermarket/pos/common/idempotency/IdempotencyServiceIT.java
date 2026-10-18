package com.supermarket.pos.common.idempotency;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Import(IdempotencyServiceIT.Config.class)
class IdempotencyServiceIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean
        FakeDocumentService fakeDocumentService(IdempotencyService idempotency, JdbcTemplate jdbc) {
            return new FakeDocumentService(idempotency, jdbc);
        }
    }

    /** Simulates a business transaction: register the key, do slow work, write one row. */
    static class FakeDocumentService {
        private final IdempotencyService idempotency;
        private final JdbcTemplate jdbc;

        FakeDocumentService(IdempotencyService idempotency, JdbcTemplate jdbc) {
            this.idempotency = idempotency;
            this.jdbc = jdbc;
        }

        @Transactional
        public Long create(IdempotencyService.Request request, String payload, long workMillis) {
            IdempotencyRecord record = idempotency.register(request);
            sleep(workMillis);
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO audit_logs (occurred_at, action, outcome, summary) "
                                + "VALUES (UTC_TIMESTAMP(6), 'IDEM_TEST', 'SUCCESS', ?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, payload);
                return ps;
            }, keys);
            Long id = keys.getKey().longValue();
            idempotency.complete(record, "AUDIT", id, 201);
            return id;
        }

        private static void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Autowired
    IdempotencyService idempotency;
    @Autowired
    FakeDocumentService documents;
    @Autowired
    JdbcTemplate jdbc;

    Long userId;

    @BeforeEach
    void createUser() {
        String username = "idem-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO users (username, full_name, password_hash, status, created_at, updated_at) "
                + "VALUES (?, 'Idem Test', 'x', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))", username);
        userId = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    @Test
    void sequentialRetryReplaysTheOriginalResult() {
        String key = UUID.randomUUID().toString();
        IdempotencyService.Request request = idempotency.request(userId, "TEST", key, Map.of("item", "A"));

        var first = idempotency.execute(request, () -> documents.create(request, key, 0), id -> id);
        var second = idempotency.execute(request, () -> documents.create(request, key, 0), id -> id);

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.value()).isEqualTo(first.value());
        assertThat(countRows(key)).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentBodyIsRejected() {
        String key = UUID.randomUUID().toString();
        IdempotencyService.Request original = idempotency.request(userId, "TEST", key, Map.of("item", "A"));
        idempotency.execute(original, () -> documents.create(original, key, 0), id -> id);

        IdempotencyService.Request changed = idempotency.request(userId, "TEST", key, Map.of("item", "B"));
        assertThatThrownBy(() -> idempotency.execute(changed, () -> documents.create(changed, key, 0), id -> id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void concurrentDuplicatesExecuteExactlyOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        IdempotencyService.Request request = idempotency.request(userId, "TEST", key, Map.of("item", "C"));
        int callers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<IdempotencyService.Outcome<Long>>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return idempotency.execute(request, () -> documents.create(request, key, 400), id -> id);
                }));
            }
            start.countDown();
            List<IdempotencyService.Outcome<Long>> outcomes = new ArrayList<>();
            for (var f : futures) {
                outcomes.add(f.get());
            }
            assertThat(outcomes).filteredOn(o -> !o.replayed()).hasSize(1);
            assertThat(outcomes).extracting(IdempotencyService.Outcome::value)
                    .containsOnly(outcomes.get(0).value());
            assertThat(countRows(key)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void missingOrMalformedKeyIsRejected() {
        assertThatThrownBy(() -> idempotency.request(userId, "TEST", null, Map.of()))
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        assertThatThrownBy(() -> idempotency.request(userId, "TEST", "bad key!", Map.of()))
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    private int countRows(String payload) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'IDEM_TEST' AND summary = ?",
                Integer.class, payload);
    }
}
