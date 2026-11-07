package com.supermarket.pos.shop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.pos.auth.config.RateLimitFilter;
import com.supermarket.pos.inventory.entity.MovementType;
import com.supermarket.pos.inventory.service.InventoryService;
import com.supermarket.pos.shop.service.RescueDealService;
import com.supermarket.pos.support.ApiClient;
import com.supermarket.pos.support.IntegrationTest;
import com.supermarket.pos.support.TestData;
import com.supermarket.pos.support.TestData.TestUser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class ShopFlowIT {

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
    RescueDealService rescue;
    @Autowired
    TransactionTemplate tx;

    Long storeId;
    TestUser staff;
    Long registerId;

    @BeforeEach
    void setUp() {
        rateLimit.reset();
        storeId = data.store("SHOP1");
        staff = data.user("MANAGER", storeId);
        registerId = data.register(storeId, "W" + UUID.randomUUID().toString().substring(0, 3).toUpperCase());
    }

    private Long stock(String price, String qty) {
        Long product = data.product("EACH", price, false);
        tx.executeWithoutResult(s -> inventory.receive(storeId, MovementType.OPENING_BALANCE,
                new InventoryService.Reference("TEST", System.nanoTime(), staff.id(), "stock"),
                List.of(new InventoryService.Receipt(product, new BigDecimal(qty), new BigDecimal("40"), null, null, 1L, null, null))));
        return product;
    }

    private BigDecimal onHand(Long product) {
        return jdbc.queryForObject("SELECT quantity_on_hand FROM inventory_balances WHERE store_id = ? AND product_id = ?",
                BigDecimal.class, storeId, product);
    }

    private ApiClient shopper() throws Exception {
        ApiClient c = new ApiClient(mvc).fetchCsrf();
        String email = "shopper" + UUID.randomUUID().toString().substring(0, 6) + "@example.test";
        String phone = "07" + String.valueOf(System.nanoTime()).substring(5, 13);
        c.perform(post("/api/v1/shop/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Online Shopper","email":"%s","phone":"%s","password":"Shopping1","marketingConsent":false}
                        """.formatted(email, phone)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loyaltyCard").exists());
        return c;
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    void clickAndCollectWithSubstitutionEndsAsOneSaleAndPicksStockOnce() throws Exception {
        Long milk = stock("100.00", "10");
        Long bread = stock("200.00", "5");
        Long brownBread = stock("150.00", "5");
        ApiClient customer = shopper();

        JsonNode quote = body(customer.perform(post("/api/v1/shop/public/cart/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeId\":%d,\"items\":[{\"productId\":%d,\"quantity\":\"2\"},{\"productId\":%d,\"quantity\":\"1\"}]}"
                        .formatted(storeId, milk, bread))));
        assertThat(quote.get("grandTotal").asText()).isEqualTo("400.00");

        JsonNode order = body(customer.perform(post("/api/v1/shop/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"storeId":%d,"expectedTotal":"400.00","items":[{"productId":%d,"quantity":"2"},
                         {"productId":%d,"quantity":"1","substitutionPreference":"SUBSTITUTE"}]}""".formatted(storeId, milk, bread)))
                .andExpect(status().isCreated()));
        long orderId = order.get("id").asLong();
        String number = order.get("orderNumber").asText();
        String code = order.get("pickupCode").asText();
        long milkLine = order.get("lines").get(0).get("id").asLong();
        long breadLine = order.get("lines").get(1).get("id").asLong();

        ApiClient store = new ApiClient(mvc);
        store.login(staff.username(), TestData.PASSWORD);
        store.perform(post("/api/v1/online-orders/" + orderId + "/start-packing")).andExpect(status().isOk());
        store.perform(post("/api/v1/online-orders/" + orderId + "/items/" + milkLine + "/pick")).andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.resolved").value(1));
        // Bread is out: propose brown bread; it is cheaper, so the customer pays 150 instead of 200.
        store.perform(post("/api/v1/online-orders/" + orderId + "/items/" + breadLine + "/substitute")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":%d,\"quantity\":\"1\"}".formatted(brownBread)))
                .andExpect(jsonPath("$.lines[1].pickStatus").value("SUBSTITUTE_PROPOSED"));
        store.perform(post("/api/v1/online-orders/" + orderId + "/ready")).andExpect(status().is4xxClientError());

        customer.perform(get("/api/v1/shop/orders/" + number))
                .andExpect(jsonPath("$.progress.pendingSubstitutions").value(1));
        customer.perform(post("/api/v1/shop/orders/" + number + "/items/" + breadLine + "/substitution")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accept\":true}"))
                .andExpect(jsonPath("$.lines[1].pickStatus").value("SUBSTITUTED"));

        store.perform(post("/api/v1/online-orders/" + orderId + "/ready")).andExpect(status().isOk())
                .andExpect(jsonPath("$.finalTotal").value("350.00"))
                .andExpect(jsonPath("$.status").value("READY_FOR_PICKUP"));
        assertThat(onHand(milk)).isEqualByComparingTo("8");
        assertThat(onHand(bread)).isEqualByComparingTo("5");
        assertThat(onHand(brownBread)).isEqualByComparingTo("4");

        customer.perform(post("/api/v1/shop/orders/" + number + "/arrived")).andExpect(jsonPath("$.arrivedAt").exists());
        store.perform(get("/api/v1/online-orders/summary")).andExpect(jsonPath("$.arrivedWaiting").value(1));

        store.perform(post("/api/v1/pos/shifts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"registerId\":%d,\"openingFloat\":\"1000.00\"}".formatted(registerId))).andExpect(status().isCreated());
        store.perform(post("/api/v1/online-orders/" + orderId + "/collect").header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"registerId":%d,"pickupCode":"%s","expectedTotal":"350.00",
                                 "payments":[{"tenderType":"CASH","amountTendered":"500.00"}]}""".formatted(registerId, code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.channel").value("ONLINE"))
                .andExpect(jsonPath("$.totals.grandTotal").value("350.00"))
                .andExpect(jsonPath("$.changeDue").value("150.00"))
                .andExpect(jsonPath("$.onlineOrderNumber").value(number));
        // Collection did not deduct stock a second time.
        assertThat(onHand(milk)).isEqualByComparingTo("8");
        customer.perform(get("/api/v1/shop/orders/" + number)).andExpect(jsonPath("$.status").value("COLLECTED"));
        Integer unread = jdbc.queryForObject("SELECT COUNT(*) FROM customer_notifications n JOIN online_orders o ON o.id = n.online_order_id WHERE o.id = ?",
                Integer.class, orderId);
        assertThat(unread).isGreaterThanOrEqualTo(5); // received, packing, substitution, ready, collected
    }

    @Test
    void cancellingAfterPackingPutsTheStockBack() throws Exception {
        Long rice = stock("500.00", "3");
        ApiClient customer = shopper();
        JsonNode order = body(customer.perform(post("/api/v1/shop/orders").contentType(MediaType.APPLICATION_JSON).content(
                "{\"storeId\":%d,\"expectedTotal\":\"1000.00\",\"items\":[{\"productId\":%d,\"quantity\":\"2\"}]}".formatted(storeId, rice))));
        long id = order.get("id").asLong();
        long line = order.get("lines").get(0).get("id").asLong();
        ApiClient store = new ApiClient(mvc);
        store.login(staff.username(), TestData.PASSWORD);
        store.perform(post("/api/v1/online-orders/" + id + "/start-packing"));
        store.perform(post("/api/v1/online-orders/" + id + "/items/" + line + "/pick"));
        store.perform(post("/api/v1/online-orders/" + id + "/ready")).andExpect(status().isOk());
        assertThat(onHand(rice)).isEqualByComparingTo("1");
        store.perform(post("/api/v1/online-orders/" + id + "/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Customer called\"}")).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(onHand(rice)).isEqualByComparingTo("3");
    }

    @Test
    void customerAndStaffTokensNeverCrossOver() throws Exception {
        ApiClient customer = shopper();
        String shopToken = customer.cookie("SHOP_AT");
        // A customer token sent to a back-office endpoint is refused...
        mvc.perform(get("/api/v1/users").cookie(new Cookie("POS_AT", shopToken))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/sales").header("Authorization", "Bearer " + shopToken)).andExpect(status().isForbidden());
        // ...and a staff token cannot act as a customer.
        ApiClient store = new ApiClient(mvc);
        store.login(staff.username(), TestData.PASSWORD);
        mvc.perform(get("/api/v1/shop/me").cookie(new Cookie("SHOP_AT", store.cookie("POS_AT")))).andExpect(status().isForbidden());
    }

    @Test
    void nearExpiryBatchBecomesARescueDealForThatStoreOnly() throws Exception {
        Long yogurt = data.product("EACH", "100.00", true);
        tx.executeWithoutResult(s -> inventory.receive(storeId, MovementType.PURCHASE_RECEIPT,
                new InventoryService.Reference("GOODS_RECEIPT", System.nanoTime(), staff.id(), "test"),
                List.of(new InventoryService.Receipt(yogurt, new BigDecimal("6"), new BigDecimal("50"), "Y-1",
                        LocalDate.now().plusDays(2), 1L, null, null))));
        var result = rescue.run(storeId);
        assertThat(result.created()).isGreaterThanOrEqualTo(1);

        ApiClient visitor = new ApiClient(mvc).fetchCsrf();
        JsonNode deals = body(visitor.perform(get("/api/v1/shop/public/rescue-deals").param("storeId", String.valueOf(storeId))));
        JsonNode deal = null;
        for (JsonNode d : deals) {
            if (d.get("productId").asLong() == yogurt) {
                deal = d;
            }
        }
        assertThat(deal).isNotNull();
        assertThat(deal.get("offerPrice").asText()).isEqualTo("70.00"); // 30% off
        // Another store does not get the deal.
        Long otherStore = data.store("SHOP2");
        JsonNode elsewhere = body(visitor.perform(get("/api/v1/shop/public/products/" + yogurt).param("storeId", String.valueOf(otherStore))));
        assertThat(elsewhere.get("offerPrice").isNull()).isTrue();
        // Running again does not duplicate the deal.
        assertThat(rescue.run(storeId).created()).isZero();
    }

    @Test
    void aLapsedRescueDealIsSwitchedOffAndTheBatchCanGetANewOne() throws Exception {
        Long cheese = data.product("EACH", "200.00", true);
        tx.executeWithoutResult(s -> inventory.receive(storeId, MovementType.PURCHASE_RECEIPT,
                new InventoryService.Reference("GOODS_RECEIPT", System.nanoTime(), staff.id(), "test"),
                List.of(new InventoryService.Receipt(cheese, new BigDecimal("4"), new BigDecimal("90"), "C-1",
                        LocalDate.now().plusDays(1), 1L, null, null))));
        assertThat(rescue.run(storeId).created()).isGreaterThanOrEqualTo(1);
        // The deal's end time passes while it is still flagged active (e.g. the expiry date is later corrected).
        jdbc.update("""
                UPDATE product_promotions SET starts_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 2 DAY), ends_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 HOUR)
                WHERE source = 'RESCUE' AND product_id = ?""", cheese);
        var again = rescue.run(storeId);
        assertThat(again.ended()).isGreaterThanOrEqualTo(1);
        Integer running = jdbc.queryForObject("""
                SELECT COUNT(*) FROM product_promotions WHERE source = 'RESCUE' AND product_id = ? AND active = TRUE
                  AND ends_at > UTC_TIMESTAMP(6)""", Integer.class, cheese);
        assertThat(running).isEqualTo(1);
    }
}
