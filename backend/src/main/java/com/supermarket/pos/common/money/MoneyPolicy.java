package com.supermarket.pos.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The one place that defines monetary precision and rounding. See docs/01-architecture.md §8.
 * <ul>
 *   <li>Amounts: scale 2, {@link RoundingMode#HALF_UP}.</li>
 *   <li>Unit costs: scale 4. Quantities: scale 3. Rates: percent with scale 3.</li>
 *   <li>Every intermediate line value is rounded; totals are sums of rounded values.</li>
 * </ul>
 */
public final class MoneyPolicy {

    public static final int AMOUNT_SCALE = 2;
    public static final int COST_SCALE = 4;
    public static final int QUANTITY_SCALE = 3;
    public static final int RATE_SCALE = 3;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(AMOUNT_SCALE);
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MoneyPolicy() {
    }

    /** Rounds to the amount scale. Null is treated as zero. */
    public static BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO : value.setScale(AMOUNT_SCALE, ROUNDING);
    }

    public static BigDecimal cost(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(COST_SCALE) : value.setScale(COST_SCALE, ROUNDING);
    }

    public static BigDecimal quantity(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(QUANTITY_SCALE) : value.setScale(QUANTITY_SCALE, ROUNDING);
    }

    /** unitPrice × quantity, rounded to the amount scale. */
    public static BigDecimal extend(BigDecimal unitPrice, BigDecimal quantity) {
        return amount(unitPrice.multiply(quantity));
    }

    /** {@code percent}% of {@code base}, rounded. */
    public static BigDecimal percentOf(BigDecimal base, BigDecimal percent) {
        return amount(base.multiply(percent).divide(HUNDRED, AMOUNT_SCALE + 6, ROUNDING));
    }

    /** Tax contained in a tax-inclusive amount: net × r / (100 + r). */
    public static BigDecimal taxIncluded(BigDecimal inclusiveAmount, BigDecimal ratePercent) {
        if (ratePercent.signum() == 0) {
            return ZERO;
        }
        return amount(inclusiveAmount.multiply(ratePercent)
                .divide(HUNDRED.add(ratePercent), AMOUNT_SCALE + 6, ROUNDING));
    }

    /** Tax added on top of a tax-exclusive amount: net × r / 100. */
    public static BigDecimal taxExclusive(BigDecimal exclusiveAmount, BigDecimal ratePercent) {
        return percentOf(exclusiveAmount, ratePercent);
    }

    public static boolean isNegative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }

    /**
     * Splits {@code total} across lines in proportion to {@code weights} using the
     * largest-remainder method, so the parts always add up exactly to the total.
     * Lines with zero weight receive zero. Ties go to the earlier line, which keeps the result stable.
     */
    public static List<BigDecimal> allocate(BigDecimal total, List<BigDecimal> weights) {
        BigDecimal roundedTotal = amount(total);
        List<BigDecimal> result = new ArrayList<>(weights.size());
        BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weights.isEmpty()) {
            return result;
        }
        if (weightSum.signum() == 0 || roundedTotal.signum() == 0) {
            weights.forEach(w -> result.add(ZERO));
            return result;
        }
        BigDecimal unit = BigDecimal.ONE.movePointLeft(AMOUNT_SCALE);
        BigDecimal[] remainders = new BigDecimal[weights.size()];
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < weights.size(); i++) {
            BigDecimal exact = roundedTotal.multiply(weights.get(i)).divide(weightSum, AMOUNT_SCALE + 8, ROUNDING);
            BigDecimal floor = exact.setScale(AMOUNT_SCALE, RoundingMode.DOWN);
            result.add(floor);
            remainders[i] = exact.subtract(floor);
            allocated = allocated.add(floor);
        }
        int unitsLeft = roundedTotal.subtract(allocated).divide(unit, 0, RoundingMode.UNNECESSARY).intValueExact();
        List<Integer> order = IntStream.range(0, weights.size()).boxed()
                .sorted(Comparator.<Integer, BigDecimal>comparing(i -> remainders[i]).reversed()
                        .thenComparing(i -> i))
                .toList();
        for (int k = 0; k < unitsLeft; k++) {
            int idx = order.get(k % order.size());
            result.set(idx, result.get(idx).add(unit));
        }
        return result;
    }
}
