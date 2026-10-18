package com.supermarket.pos.products;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.pos.auth.config.RateLimitFilter;
import com.supermarket.pos.support.ApiClient;
import com.supermarket.pos.support.IntegrationTest;
import com.supermarket.pos.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class ProductCatalogIT {

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

    Long storeId;
    Long categoryId;
    Long taxRateId;
    ApiClient admin;

    @BeforeEach
    void setUp() throws Exception {
        rateLimit.reset();
        storeId = data.store("CAT1");
        categoryId = data.category("CATIT");
        taxRateId = jdbc.queryForObject("SELECT id FROM tax_rates WHERE code = 'VAT_STD'", Long.class);
        admin = new ApiClient(mvc);
        admin.login(data.user("ADMIN", storeId).username(), TestData.PASSWORD).andExpect(status().isOk());
    }

    private String productJson(String sku, String barcode, String price) {
        return """
                {"sku":"%s","name":"Catalog test %s","categoryId":%d,"unitOfMeasure":"PACK",
                 "costPrice":"80.0000","sellingPrice":"%s","taxRateId":%d,"reorderLevel":"5",
                 "trackBatches":false,"trackExpiry":false,"allowPriceOverride":false,
                 "barcodes":["%s"]}""".formatted(sku, sku, categoryId, price, taxRateId, barcode);
    }

    private static String uniqueSku() {
        return "IT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /** A valid UPC-A built from 11 random digits. */
    private static String randomUpcA() {
        String body = String.valueOf(java.util.concurrent.ThreadLocalRandom.current()
                .nextLong(10_000_000_000L, 99_999_999_999L));
        return body + com.supermarket.pos.products.service.BarcodeNormalizer.checkDigit(body);
    }

    @Test
    void upcABarcodeIsFoundByItsEan13FormAndPricesAreStrings() throws Exception {
        String upcA = randomUpcA();
        admin.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(uniqueSku(), upcA, "120.50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellingPrice").value("120.50"))
                .andExpect(jsonPath("$.barcodes[0].barcode").value("0" + upcA))
                .andExpect(jsonPath("$.stock.quantityOnHand").value("0.000"));

        admin.perform(get("/api/v1/products/barcode/0" + upcA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellingPrice").value("120.50"))
                .andExpect(jsonPath("$.taxRatePercent").value("18.000"));
    }

    @Test
    void duplicateSkuAndBarcodeAreRejectedWithTheField() throws Exception {
        String sku = uniqueSku();
        String upcA = randomUpcA();
        admin.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(productJson(sku, upcA, "10.00")))
                .andExpect(status().isCreated());
        admin.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(productJson(sku, randomUpcA(), "10.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.details.field").value("sku"));
        admin.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(productJson(uniqueSku(), upcA, "10.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.field").value("barcodes"));
    }

    @Test
    void invalidCheckDigitIsAValidationError() throws Exception {
        admin.perform(get("/api/v1/products/barcode/4006381333932"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BARCODE"));
        admin.perform(get("/api/v1/products/barcode/4006381333931"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BARCODE_NOT_FOUND"));
    }

    @Test
    void cashierCanLookUpButNotCreateOrSeeCost() throws Exception {
        String upcA = randomUpcA();
        String body = admin.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(uniqueSku(), upcA, "33.00")))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();

        ApiClient cashier = new ApiClient(mvc);
        cashier.login(data.user("CASHIER", storeId).username(), TestData.PASSWORD);
        cashier.perform(get("/api/v1/products/barcode/" + upcA)).andExpect(status().isOk());
        cashier.perform(get("/api/v1/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costPrice").doesNotExist());
        cashier.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(uniqueSku(), randomUpcA(), "1.00")))
                .andExpect(status().isForbidden());
    }

    @Test
    void priceChangesNeedPriceManageAndAreAudited() throws Exception {
        data.role("CATALOG_CLERK", "PRODUCT_VIEW", "PRODUCT_MANAGE");
        String sku = uniqueSku();
        String body = admin.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, randomUpcA(), "99.00")))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = json.readTree(body);
        long id = created.get("id").asLong();

        ApiClient clerk = new ApiClient(mvc);
        clerk.login(data.user("CATALOG_CLERK", storeId).username(), TestData.PASSWORD);
        String update = productJson(sku, "", "149.00").replace(",\n \"barcodes\":[\"\"]", "")
                .replace("\"barcodes\":[\"\"]", "\"barcodes\":[]")
                .replace("}", ",\"version\":" + created.get("version").asLong() + "}");
        clerk.perform(put("/api/v1/products/" + id).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isForbidden());

        admin.perform(put("/api/v1/products/" + id).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellingPrice").value("149.00"));
        Integer audits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'PRICE_CHANGED' AND entity_id = ?", Integer.class,
                String.valueOf(id));
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void openingBalanceThenWriteOffAboveThresholdNeedsApproval() throws Exception {
        String sku = uniqueSku();
        String body = admin.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, randomUpcA(), "500.00")))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();

        admin.perform(post("/api/v1/inventory/adjustments").header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"type":"OPENING_BALANCE","reason":"Initial count",
                                 "items":[{"productId":%d,"quantity":"100","unitCost":"80.00"}]}""".formatted(id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalValue").value("8000.00"));

        // Inventory staff may write off, but 80 × 80.00 = 6,400 exceeds the 5,000 threshold.
        ApiClient staff = new ApiClient(mvc);
        staff.login(data.user("INVENTORY_STAFF", storeId).username(), TestData.PASSWORD);
        String writeOff = """
                {"type":"WRITE_OFF_DAMAGED","reason":"Water damage",
                 "items":[{"productId":%d,"quantity":"80"}]}""".formatted(id);
        staff.perform(post("/api/v1/inventory/adjustments").header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(writeOff))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_REQUIRED"));

        // A small write-off is fine; replaying the same key returns the same document.
        String key = UUID.randomUUID().toString();
        String small = writeOff.replace("\"80\"", "\"2\"");
        String first = staff.perform(post("/api/v1/inventory/adjustments").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(small))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String replay = staff.perform(post("/api/v1/inventory/adjustments").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(small))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(replay).get("id")).isEqualTo(json.readTree(first).get("id"));

        admin.perform(get("/api/v1/inventory/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock.quantityOnHand").value("98.000"));
    }

    @Test
    void lowStockFilterUsesTheLedger() throws Exception {
        String sku = uniqueSku();
        admin.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, randomUpcA(), "5.00")))
                .andExpect(status().isCreated());
        admin.perform(get("/api/v1/products").param("lowStock", "true").param("q", sku))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value(sku))
                .andExpect(jsonPath("$.content[0].stock.lowStock").value(true));
    }
}
