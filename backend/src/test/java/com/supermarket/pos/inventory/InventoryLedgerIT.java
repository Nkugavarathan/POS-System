package com.supermarket.pos.inventory;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.inventory.entity.MovementType;
import com.supermarket.pos.inventory.service.InventoryService;
import com.supermarket.pos.inventory.service.InventoryService.Allocation;
import com.supermarket.pos.inventory.service.InventoryService.Deduction;
import com.supermarket.pos.inventory.service.InventoryService.Receipt;
import com.supermarket.pos.inventory.service.InventoryService.Reference;
import com.supermarket.pos.support.IntegrationTest;
import com.supermarket.pos.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class InventoryLedgerIT {

    private static final AtomicLong REF = new AtomicLong(1_000_000);

    @Autowired
    InventoryService inventory;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TestData data;

    Long storeId;
    Long userId;

    @BeforeEach
    void setUp() {
        storeId = data.store("INV1");
        userId = data.user("INVENTORY_STAFF", storeId).id();
    }

    private Reference ref(String type) {
        return new Reference(type, REF.incrementAndGet(), userId, "test");
    }

    private void receive(Long productId, String qty, String cost) {
        tx.executeWithoutResult(s -> inventory.receive(storeId, MovementType.PURCHASE_RECEIPT, ref("TEST_IN"),
                List.of(new Receipt(productId, new BigDecimal(qty), new BigDecimal(cost), null, null, 1L, null, null))));
    }

    private BigDecimal onHand(Long productId) {
        return jdbc.queryForObject("SELECT quantity_on_hand FROM inventory_balances WHERE store_id = ? AND product_id = ?",
                BigDecimal.class, storeId, productId);
    }

    @Test
    void receiptsMaintainMovingAverageCostAndLedgerInvariants() {
        Long product = data.product("EACH", "200.00", false);
        receive(product, "10", "100.00");
        receive(product, "10", "130.00");

        assertThat(onHand(product)).isEqualByComparingTo("20");
        BigDecimal avg = jdbc.queryForObject("SELECT average_cost FROM inventory_balances WHERE store_id = ? AND product_id = ?",
                BigDecimal.class, storeId, product);
        assertThat(avg).isEqualByComparingTo("115.0000");

        // Every movement satisfies after = before + change and the last "after" equals the balance.
        List<BigDecimal[]> rows = jdbc.query(
                "SELECT quantity_before, quantity_change, quantity_after FROM stock_movements WHERE product_id = ? ORDER BY id",
                (rs, i) -> new BigDecimal[]{rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3)}, product);
        assertThat(rows).hasSize(2);
        rows.forEach(r -> assertThat(r[0].add(r[1])).isEqualByComparingTo(r[2]));
        assertThat(rows.get(1)[2]).isEqualByComparingTo(onHand(product));
    }

    @Test
    void deductingMoreThanAvailableFailsAndChangesNothing() {
        Long product = data.product("EACH", "50.00", false);
        receive(product, "3", "20.00");

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> inventory.deduct(storeId, MovementType.SALE, ref("TEST_SALE"),
                List.of(new Deduction(product, new BigDecimal("2"), 1L), new Deduction(product, new BigDecimal("2"), 2L)))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
        assertThat(onHand(product)).isEqualByComparingTo("3");
        Integer saleMovements = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_movements WHERE product_id = ? AND movement_type = 'SALE'", Integer.class, product);
        assertThat(saleMovements).isZero();
    }

    @Test
    void batchesAreAllocatedFirstExpiryFirstOutWithExpiredStockLast() {
        Long product = data.product("EACH", "90.00", true);
        LocalDate today = LocalDate.now();
        tx.executeWithoutResult(s -> inventory.receive(storeId, MovementType.PURCHASE_RECEIPT, ref("GOODS_RECEIPT"), List.of(
                new Receipt(product, new BigDecimal("5"), new BigDecimal("60"), "LATE", today.plusDays(10), 1L, null, null),
                new Receipt(product, new BigDecimal("5"), new BigDecimal("50"), "SOON", today.plusDays(2), 2L, null, null),
                new Receipt(product, new BigDecimal("5"), new BigDecimal("40"), "OLD", today.minusDays(1), 3L, null, null))));

        List<Allocation> first = tx.execute(s -> inventory.deduct(storeId, MovementType.SALE, ref("SALE"),
                List.of(new Deduction(product, new BigDecimal("7"), 1L))));
        assertThat(first.get(0).portions()).extracting(InventoryService.BatchPortion::batchNumber)
                .containsExactly("SOON", "LATE");
        assertThat(first.get(0).usedExpiredBatch()).isFalse();
        // cost = 5 × 50 + 2 × 60
        assertThat(first.get(0).costAmount()).isEqualByComparingTo("370.00");

        List<Allocation> second = tx.execute(s -> inventory.deduct(storeId, MovementType.SALE, ref("SALE"),
                List.of(new Deduction(product, new BigDecimal("8"), 1L))));
        assertThat(second.get(0).portions()).extracting(InventoryService.BatchPortion::batchNumber)
                .containsExactly("LATE", "OLD");
        assertThat(second.get(0).usedExpiredBatch()).isTrue();
        assertThat(onHand(product)).isEqualByComparingTo("0");
    }

    @Test
    void theSameDocumentLineCannotBePostedTwice() {
        Long product = data.product("EACH", "10.00", false);
        receive(product, "10", "5.00");
        Reference sale = ref("SALE");
        tx.executeWithoutResult(s -> inventory.deduct(storeId, MovementType.SALE, sale,
                List.of(new Deduction(product, BigDecimal.ONE, 77L))));
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> inventory.deduct(storeId, MovementType.SALE, sale,
                List.of(new Deduction(product, BigDecimal.ONE, 77L)))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(onHand(product)).isEqualByComparingTo("9");
    }

    @Test
    void concurrentSalesNeverOversell() throws Exception {
        Long product = data.product("EACH", "10.00", false);
        receive(product, "5", "4.00");
        int buyers = 10;
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < buyers; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        tx.executeWithoutResult(s -> inventory.deduct(storeId, MovementType.SALE, ref("SALE"),
                                List.of(new Deduction(product, BigDecimal.ONE, 1L))));
                        return true;
                    } catch (ApiException e) {
                        assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
                        return false;
                    }
                }));
            }
            start.countDown();
            int sold = 0;
            for (Future<Boolean> f : results) {
                sold += f.get() ? 1 : 0;
            }
            assertThat(sold).isEqualTo(5);
            assertThat(onHand(product)).isEqualByComparingTo("0");
            Integer movements = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM stock_movements WHERE product_id = ? AND movement_type = 'SALE'", Integer.class, product);
            assertThat(movements).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void databaseRejectsNegativeStockEvenIfCodeWereBypassed() {
        Long product = data.product("EACH", "10.00", false);
        receive(product, "1", "4.00");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE inventory_balances SET quantity_on_hand = -1 WHERE store_id = ? AND product_id = ?", storeId, product))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
