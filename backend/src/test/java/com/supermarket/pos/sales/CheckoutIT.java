package com.supermarket.pos.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.pos.auth.config.RateLimitFilter;
import com.supermarket.pos.inventory.entity.MovementType;
import com.supermarket.pos.inventory.service.InventoryService;
import com.supermarket.pos.support.ApiClient;
import com.supermarket.pos.support.IntegrationTest;
import com.supermarket.pos.support.TestData;
import com.supermarket.pos.support.TestData.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class CheckoutIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestData data;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;
    @Autowired
    RateLimitFilter rateLimit;
    @Autowired
    InventoryService inventory;
    @Autowired
    TransactionTemplate tx;

    Long storeId;
    TestUser cashier;
    Long registerId;
    ApiClient till;

    @BeforeEach
    void setUp() throws Exception {
        rateLimit.reset();
        storeId = data.store("SALE1");
        cashier = data.user("CASHIER", storeId);
        registerId = data.register(storeId, "R" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        till = openTill(cashier, registerId);
    }

    private ApiClient openTill(TestUser user, Long register) throws Exception {
        ApiClient client = new ApiClient(mvc);
        client.login(user.username(), TestData.PASSWORD).andExpect(status().isOk());
        client.perform(post("/api/v1/pos/shifts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registerId\":%d,\"openingFloat\":\"5000.00\"}".formatted(register)))
                .andExpect(status().isCreated());
        return client;
    }

    private Long stockedProduct(String price, String qty) {
        Long product = data.product("EACH", price, false);
        Long userId = cashier.id();
        tx.executeWithoutResult(s -> inventory.receive(storeId, MovementType.OPENING_BALANCE,
                new InventoryService.Reference("TEST", System.nanoTime(), userId, "test stock"),
                List.of(new InventoryService.Receipt(product, new BigDecimal(qty), new BigDecimal("50.00"), null, null, 1L, null, null))));
        return product;
    }

    private String checkoutBody(Long product, String qty, String expected, String payments) {
        return """
                {"registerId":%d,"items":[{"productId":%d,"quantity":"%s"}],
                 "expectedGrandTotal":"%s","payments":%s}""".formatted(registerId, product, qty, expected, payments);
    }

    private BigDecimal onHand(Long product) {
        return jdbc.queryForObject("SELECT quantity_on_hand FROM inventory_balances WHERE store_id = ? AND product_id = ?",
                BigDecimal.class, storeId, product);
    }

    @Test
    void cashSaleRecordsSalePaymentsStockAndChange() throws Exception {
        Long product = stockedProduct("118.00", "10");
        till.perform(post("/api/v1/sales/quote").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registerId\":%d,\"items\":[{\"productId\":%d,\"quantity\":\"3\"}]}".formatted(registerId, product)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.grandTotal").value("354.00"))
                // 354.00 incl. 18% VAT contains 54.00 tax
                .andExpect(jsonPath("$.totals.taxTotal").value("54.00"));

        String key = UUID.randomUUID().toString();
        String body = checkoutBody(product, "3", "354.00", "[{\"tenderType\":\"CASH\",\"amountTendered\":\"500.00\"}]");
        String sale = till.perform(post("/api/v1/sales/checkout").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptNumber").exists())
                .andExpect(jsonPath("$.changeDue").value("146.00"))
                .andExpect(jsonPath("$.payments[0].amount").value("354.00"))
                .andReturn().getResponse().getContentAsString();
        long saleId = json.readTree(sale).get("id").asLong();
        assertThat(onHand(product)).isEqualByComparingTo("7");

        // Retrying the same request (e.g. after a timeout) replays the sale; nothing is duplicated.
        till.perform(post("/api/v1/sales/checkout").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(saleId));
        assertThat(onHand(product)).isEqualByComparingTo("7");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE sale_id = ?", Integer.class, saleId)).isEqualTo(1);

        // Reprint: marked as a copy, no new sale.
        till.perform(post("/api/v1/sales/" + saleId + "/reprints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reprint").value(true));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales WHERE shift_id = (SELECT shift_id FROM sales WHERE id = ?)",
                Integer.class, saleId)).isEqualTo(1);
    }

    @Test
    void splitCardAndCashPayment() throws Exception {
        Long product = stockedProduct("1000.00", "5");
        till.perform(post("/api/v1/sales/checkout").header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(checkoutBody(product, "2", "2000.00", """
                                [{"tenderType":"CARD","amount":"1500.00","card":{"brand":"visa","last4":"4242","authCode":"A1B2C3"}},
                                 {"tenderType":"CASH","amountTendered":"1000.00"}]""")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payments[0].tenderType").value("CARD"))
                .andExpect(jsonPath("$.payments[0].cardLast4").value("4242"))
                .andExpect(jsonPath("$.payments[1].amount").value("500.00"))
                .andExpect(jsonPath("$.changeDue").value("500.00"));
    }

    @Test
    void insufficientTenderAndChangedTotalAreRejectedWithoutSideEffects() throws Exception {
        Long product = stockedProduct("100.00", "5");
        till.perform(post("/api/v1/sales/checkout").header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(checkoutBody(product, "2", "200.00",
                                "[{\"tenderType\":\"CASH\",\"amountTendered\":\"150.00\"}]")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_TENDER"));
        till.perform(post("/api/v1/sales/checkout").header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(checkoutBody(product, "2", "190.00",
                                "[{\"tenderType\":\"CASH\",\"amountTendered\":\"200.00\"}]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_MISMATCH"))
                .andExpect(jsonPath("$.details.grandTotal").value("200.00"));
        assertThat(onHand(product)).isEqualByComparingTo("5");
    }

    @Test
    void notEnoughStockRollsBackEverythingIncludingTheReceiptNumber() throws Exception {
        Long product = stockedProduct("10.00", "1");
        Integer salesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM sales", Integer.class);
        till.perform(post("/api/v1/sales/checkout").header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(checkoutBody(product, "2", "20.00",
                                "[{\"tenderType\":\"CASH\",\"amountTendered\":\"20.00\"}]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales", Integer.class)).isEqualTo(salesBefore);
        assertThat(onHand(product)).isEqualByComparingTo("1");
    }

    @Test
    void discountAboveCashierLimitNeedsManagerApproval() throws Exception {
        Long product = stockedProduct("200.00", "5");
        TestUser manager = data.user("MANAGER", storeId);
        String lines = "[{\"productId\":%d,\"quantity\":\"1\",\"manualDiscount\":{\"method\":\"PERCENT\",\"value\":\"10\",\"reason\":\"Dented\"}}]"
                .formatted(product);
        // Cashier limit is 5%, so 10% requires approval.
        till.perform(post("/api/v1/sales/quote").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registerId\":%d,\"items\":%s}".formatted(registerId, lines)))
                .andExpect(jsonPath("$.totals.grandTotal").value("180.00"))
                .andExpect(jsonPath("$.requiredApprovals[0].action").value("DISCOUNT_OVER_LIMIT"))
                .andExpect(jsonPath("$.requiredApprovals[0].satisfied").value(false));
        String noApproval = "{\"registerId\":%d,\"items\":%s,\"expectedGrandTotal\":\"180.00\",\"payments\":[{\"tenderType\":\"CASH\",\"amountTendered\":\"180\"}]}"
                .formatted(registerId, lines);
        till.perform(post("/api/v1/sales/checkout").header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(noApproval))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_REQUIRED"));

        String grant = till.perform(post("/api/v1/approvals").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"action\":\"DISCOUNT_OVER_LIMIT\",\"username\":\"%s\",\"secret\":\"%s\",\"value\":\"10.00\"}"
                                .formatted(manager.username(), TestData.PIN)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(grant).get("approvalToken").asText();
        String withApproval = noApproval.replace("\"expectedGrandTotal\"", "\"approvalTokens\":[\"" + token + "\"],\"expectedGrandTotal\"");
        String sale = till.perform(post("/api/v1/sales/checkout").header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(withApproval))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long saleId = json.readTree(sale).get("id").asLong();
        Long approver = jdbc.queryForObject("SELECT approved_by FROM sale_discounts WHERE sale_id = ? AND source = 'MANUAL_LINE'",
                Long.class, saleId);
        assertThat(approver).isEqualTo(manager.id());
    }

    @Test
    void twoTillsRacingForTheLastUnitSellItOnce() throws Exception {
        Long product = stockedProduct("75.00", "1");
        TestUser second = data.user("CASHIER", storeId);
        Long register2 = data.register(storeId, "R" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        ApiClient till2 = openTill(second, register2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Object[] t : new Object[][]{{till, registerId}, {till2, register2}}) {
                results.add(pool.submit(() -> {
                    start.await();
                    String body = "{\"registerId\":%d,\"items\":[{\"productId\":%d,\"quantity\":\"1\"}],\"expectedGrandTotal\":\"75.00\",\"payments\":[{\"tenderType\":\"CASH\",\"amountTendered\":\"100\"}]}"
                            .formatted((Long) t[1], product);
                    return ((ApiClient) t[0]).perform(post("/api/v1/sales/checkout")
                                    .header("Idempotency-Key", UUID.randomUUID().toString())
                                    .contentType(MediaType.APPLICATION_JSON).content(body))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(onHand(product)).isEqualByComparingTo("0");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void shiftSummaryReflectsCashSalesAndCloseComputesVariance() throws Exception {
        Long product = stockedProduct("250.00", "4");
        till.perform(post("/api/v1/sales/checkout").header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(checkoutBody(product, "2", "500.00",
                        "[{\"tenderType\":\"CASH\",\"amountTendered\":\"1000.00\"}]"))).andExpect(status().isCreated());
        JsonNode shift = json.readTree(till.perform(get("/api/v1/pos/shifts/current")).andReturn().getResponse().getContentAsString());
        long shiftId = shift.get("id").asLong();
        till.perform(get("/api/v1/pos/shifts/" + shiftId + "/summary"))
                .andExpect(jsonPath("$.cashSales").value("500.00"))
                .andExpect(jsonPath("$.expectedCash").value("5500.00"))
                .andExpect(jsonPath("$.salesCount").value(1));
        till.perform(post("/api/v1/pos/shifts/" + shiftId + "/close").header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"countedCash\":\"5480.00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shift.status").value("CLOSED"))
                .andExpect(jsonPath("$.shift.variance").value("-20.00"));
        // No more sales on a closed shift.
        till.perform(post("/api/v1/sales/checkout").header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(checkoutBody(product, "1", "250.00",
                                "[{\"tenderType\":\"CASH\",\"amountTendered\":\"250.00\"}]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHIFT_NOT_OPEN"));
    }
}
