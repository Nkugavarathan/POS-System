package com.supermarket.pos.common.sequence;

import com.supermarket.pos.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class SequenceServiceIT {

    @Autowired
    SequenceService sequences;
    @Autowired
    TransactionTemplate tx;

    @Test
    void numbersAreSequentialPerScopeAndDay() {
        LocalDate day = LocalDate.of(2026, 10, 18);
        String first = tx.execute(s -> sequences.nextDailyNumber("RECEIPT", "T01-R01", day, 6));
        String second = tx.execute(s -> sequences.nextDailyNumber("RECEIPT", "T01-R01", day, 6));
        String otherRegister = tx.execute(s -> sequences.nextDailyNumber("RECEIPT", "T01-R02", day, 6));
        String nextDay = tx.execute(s -> sequences.nextDailyNumber("RECEIPT", "T01-R01", day.plusDays(1), 6));

        assertThat(first).isEqualTo("T01-R01-20261018-000001");
        assertThat(second).isEqualTo("T01-R01-20261018-000002");
        assertThat(otherRegister).isEqualTo("T01-R02-20261018-000001");
        assertThat(nextDay).isEqualTo("T01-R01-20261019-000001");
    }

    @Test
    void rolledBackTransactionReleasesItsNumber() {
        tx.executeWithoutResult(s -> {
            sequences.next("GAPTEST", "S1", "D");
            s.setRollbackOnly();
        });
        Long value = tx.execute(s -> sequences.next("GAPTEST", "S1", "D"));
        assertThat(value).isEqualTo(1L);
    }

    @Test
    void concurrentCallersNeverReceiveTheSameNumber() throws Exception {
        int threads = 8;
        int perThread = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<List<Long>>> tasks = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                tasks.add(() -> {
                    List<Long> got = new ArrayList<>();
                    for (int i = 0; i < perThread; i++) {
                        got.add(tx.execute(s -> sequences.next("CONC", "SCOPE", "P")));
                    }
                    return got;
                });
            }
            Set<Long> all = new HashSet<>();
            for (Future<List<Long>> f : pool.invokeAll(tasks)) {
                all.addAll(f.get());
            }
            assertThat(all).hasSize(threads * perThread);
            assertThat(all).allSatisfy(v -> assertThat(v).isBetween(1L, (long) threads * perThread));
        } finally {
            pool.shutdownNow();
        }
    }
}
