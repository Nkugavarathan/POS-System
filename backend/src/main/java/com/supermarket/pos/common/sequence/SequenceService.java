package com.supermarket.pos.common.sequence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Gap-free document numbers (receipts, GRNs, returns, ...). The counter row is created on
 * demand and then locked with SELECT ... FOR UPDATE inside the <em>caller's</em> transaction,
 * so a rolled-back business transaction also rolls back its number. Scopes are narrow
 * (store + register + day) so different registers never contend for the same row.
 */
@Service
public class SequenceService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final JdbcTemplate jdbc;

    public SequenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns the next value (1, 2, 3, ...) for the given counter. Must run inside a transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long next(String sequenceType, String scopeCode, String periodKey) {
        // Creates the row if missing. If it already exists, the no-op update just takes the row lock.
        jdbc.update("""
                INSERT INTO document_sequences (sequence_type, scope_code, period_key, next_value)
                VALUES (?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE next_value = next_value
                """, sequenceType, scopeCode, periodKey);
        Long current = jdbc.queryForObject("""
                SELECT next_value FROM document_sequences
                WHERE sequence_type = ? AND scope_code = ? AND period_key = ?
                FOR UPDATE
                """, Long.class, sequenceType, scopeCode, periodKey);
        jdbc.update("""
                UPDATE document_sequences SET next_value = next_value + 1
                WHERE sequence_type = ? AND scope_code = ? AND period_key = ?
                """, sequenceType, scopeCode, periodKey);
        return current;
    }

    /**
     * Formats {@code <scope>-<yyyyMMdd>-<000123>}, e.g. {@code CMB01-R02-20261018-000123}.
     * The day is the business day in the store's timezone.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextDailyNumber(String sequenceType, String scopeCode, LocalDate businessDay, int width) {
        String day = businessDay.format(DAY);
        long value = next(sequenceType, scopeCode, day);
        return scopeCode + "-" + day + "-" + String.format("%0" + width + "d", value);
    }

    /** Formats {@code <prefix>-<scope>-<000123>} for documents numbered per store, not per day. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextNumber(String sequenceType, String prefix, String scopeCode, int width) {
        long value = next(sequenceType, scopeCode, "ALL");
        return prefix + "-" + scopeCode + "-" + String.format("%0" + width + "d", value);
    }
}
