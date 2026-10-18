package com.supermarket.pos.pos.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * What other modules contributed to a shift (sales take cash in, refunds pay cash out).
 * Implemented by the sales and returns modules; the pos module only sums contributions, so
 * it never depends on them.
 */
public interface ShiftActivity {

    record Contribution(BigDecimal cashIn, BigDecimal cashOut, Map<String, BigDecimal> tenderAmounts,
                        Map<String, Long> tenderCounts, Map<String, Long> counters, Map<String, BigDecimal> amounts) {
    }

    Contribution contributionFor(Long shiftId);
}
