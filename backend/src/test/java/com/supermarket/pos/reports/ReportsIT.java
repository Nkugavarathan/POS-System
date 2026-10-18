package com.supermarket.pos.reports;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class ReportsIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestData data;
    @Autowired
    RateLimitFilter rateLimit;
    @Autowired
    InventoryService inventory;
    @Autowired
    TransactionTemplate tx;

    Long storeId;
    TestUser cashier;
    ApiClient till;

    @BeforeEach
    void setUp() throws Exception {
        rateLimit.reset();
        storeId = data.store("R" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        cashier = data.user("CASHIER", storeId);
        Long registerId = data.register(storeId, "P" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        Long product = data.product("EACH", "118.00", false);
        tx.executeWithoutResult(s -> inventory.receive(storeId, MovementType.OPENING_BALANCE,
                new InventoryService.Reference("TEST", System.nanoTime(), cashier.id(), "stock"),
                List.of(new InventoryService.Receipt(product, new BigDecimal("10"), new BigDecimal("50"), null, null, 1L, null, null))));
        till = new ApiClient(mvc);
        till.login(cashier.username(), TestData.PASSWORD);
        till.perform(post("/api/v1/pos/shifts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"registerId\":%d,\"openingFloat\":\"1000.00\"}".formatted(registerId))).andExpect(status().isCreated());
        till.perform(post("/api/v1/sales/checkout").header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"registerId":%d,"items":[{"productId":%d,"quantity":"2"}],"expectedGrandTotal":"236.00",
                         "payments":[{"tenderType":"CASH","amountTendered":"300.00"}]}""".formatted(registerId, product)))
                .andExpect(status().isCreated());
    }

    @Test
    void managerSeesTodaysTradingAndCanExportCsv() throws Exception {
        TestUser manager = data.user("MANAGER", storeId);
        ApiClient office = new ApiClient(mvc);
        office.login(manager.username(), TestData.PASSWORD);
        office.perform(get("/api/v1/reports/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesToday").value(1))
                .andExpect(jsonPath("$.revenueToday").value("236.00"))
                .andExpect(jsonPath("$.last14Days.length()").value(14))
                .andExpect(jsonPath("$.hoursToday.length()").value(24))
                .andExpect(jsonPath("$.topProducts7d[0].quantity").value("2.000"))
                .andExpect(jsonPath("$.openShifts").value(1));
        office.perform(get("/api/v1/reports/sales"))
                .andExpect(jsonPath("$.totals.salesCount").value(1))
                .andExpect(jsonPath("$.totals.tax").value("36.00"))
                .andExpect(jsonPath("$.tenders[0].tenderType").value("CASH"));
        String csv = office.perform(get("/api/v1/reports/sales.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("Total,1,236.00");
    }

    @Test
    void cashierCannotReadReportsAndAdminCanSearchAuditLog() throws Exception {
        till.perform(get("/api/v1/reports/sales")).andExpect(status().isForbidden());
        till.perform(get("/api/v1/audit")).andExpect(status().isForbidden());
        TestUser admin = data.user("ADMIN", storeId);
        ApiClient office = new ApiClient(mvc);
        office.login(admin.username(), TestData.PASSWORD);
        office.perform(get("/api/v1/audit").param("action", "SHIFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value(org.hamcrest.Matchers.containsString("SHIFT")));
    }
}
