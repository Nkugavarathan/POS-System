package com.supermarket.pos.common.money;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyPolicyTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @ParameterizedTest(name = "{0} rounds to {1}")
    @CsvSource({"1.005,1.01", "1.004,1.00", "2.675,2.68", "-1.005,-1.01", "0,0.00", "10,10.00"})
    void amountRoundsHalfUpToTwoPlaces(String input, String expected) {
        assertThat(MoneyPolicy.amount(d(input))).isEqualTo(d(expected));
    }

    @Test
    void extendRoundsAfterMultiplying() {
        // 0.755 kg × 1,299.00 = 980.745 → 980.75
        assertThat(MoneyPolicy.extend(d("1299.00"), d("0.755"))).isEqualTo(d("980.75"));
    }

    @Test
    void taxIncludedExtractsTaxFromGross() {
        // 1,180.00 at 18% contains exactly 180.00 tax
        assertThat(MoneyPolicy.taxIncluded(d("1180.00"), d("18.000"))).isEqualTo(d("180.00"));
        // 3,365.18 × 18 / 118 = 513.3325... → 513.33
        assertThat(MoneyPolicy.taxIncluded(d("3365.18"), d("18.000"))).isEqualTo(d("513.33"));
    }

    @Test
    void zeroRateHasNoTax() {
        assertThat(MoneyPolicy.taxIncluded(d("999.99"), d("0"))).isEqualTo(d("0.00"));
        assertThat(MoneyPolicy.taxExclusive(d("999.99"), d("0"))).isEqualTo(d("0.00"));
    }

    @Test
    void taxExclusiveAddsOnTop() {
        assertThat(MoneyPolicy.taxExclusive(d("1000.00"), d("18.000"))).isEqualTo(d("180.00"));
        assertThat(MoneyPolicy.taxExclusive(d("33.33"), d("18.000"))).isEqualTo(d("6.00"));
    }

    @Test
    void percentOf() {
        assertThat(MoneyPolicy.percentOf(d("3780.00"), d("10.00"))).isEqualTo(d("378.00"));
        assertThat(MoneyPolicy.percentOf(d("99.99"), d("12.5"))).isEqualTo(d("12.50"));
    }

    @Test
    void allocationAlwaysSumsToTotal() {
        List<BigDecimal> parts = MoneyPolicy.allocate(d("100.00"), List.of(d("1"), d("1"), d("1")));
        assertThat(parts).containsExactly(d("33.34"), d("33.33"), d("33.33"));
        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualTo(d("100.00"));
    }

    @Test
    void allocationIsProportionalWithLargestRemainder() {
        List<BigDecimal> parts = MoneyPolicy.allocate(d("100.00"),
                List.of(d("3402.00"), d("1125.00"), d("593.00")));
        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualTo(d("100.00"));
        // exact shares 66.4453 / 21.9726 / 11.5820: the floors sum to 99.99 and the leftover
        // cent goes to the largest remainder (the first line)
        assertThat(parts).containsExactly(d("66.45"), d("21.97"), d("11.58"));
    }

    @Test
    void allocationGivesNothingToZeroWeights() {
        List<BigDecimal> parts = MoneyPolicy.allocate(d("10.00"), List.of(d("0"), d("5.00"), d("0")));
        assertThat(parts).containsExactly(d("0.00"), d("10.00"), d("0.00"));
    }

    @Test
    void allocationOfZeroTotalOrZeroWeights() {
        assertThat(MoneyPolicy.allocate(d("0"), List.of(d("5"), d("5")))).containsExactly(d("0.00"), d("0.00"));
        assertThat(MoneyPolicy.allocate(d("5.00"), List.of(d("0"), d("0")))).containsExactly(d("0.00"), d("0.00"));
        assertThat(MoneyPolicy.allocate(d("5.00"), List.of())).isEmpty();
    }

    @Test
    void allocationHandlesManySmallLines() {
        List<BigDecimal> weights = java.util.Collections.nCopies(7, d("1.00"));
        List<BigDecimal> parts = MoneyPolicy.allocate(d("0.05"), weights);
        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualTo(d("0.05"));
        assertThat(parts).allSatisfy(p -> assertThat(p).isIn(d("0.00"), d("0.01")));
    }
}
