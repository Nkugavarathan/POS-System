package com.supermarket.pos.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.pos.auth.config.RateLimitFilter;
import com.supermarket.pos.auth.entity.ApprovalAction;
import com.supermarket.pos.auth.service.ApprovalService;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class ApprovalServiceIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestData data;
    @Autowired
    ApprovalService approvals;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    ObjectMapper json;
    @Autowired
    RateLimitFilter rateLimit;

    Long storeId;
    TestUser cashier;
    TestUser manager;
    ApiClient cashierClient;

    @BeforeEach
    void setUp() throws Exception {
        rateLimit.reset();
        storeId = data.store("APPR1");
        cashier = data.user("CASHIER", storeId);
        manager = data.user("MANAGER", storeId);
        cashierClient = new ApiClient(mvc);
        cashierClient.login(cashier.username(), TestData.PASSWORD).andExpect(status().isOk());
    }

    private String requestApproval(String action, String username, String secret, String value) throws Exception {
        String body = "{\"action\":\"%s\",\"username\":\"%s\",\"secret\":\"%s\"%s}".formatted(action, username, secret,
                value == null ? "" : ",\"value\":\"" + value + "\"");
        String response = cashierClient.perform(post("/api/v1/approvals").contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response);
        return node.has("approvalToken") ? node.get("approvalToken").asText() : null;
    }

    @Test
    void managerPinApprovesADiscountOnceOnly() throws Exception {
        String token = requestApproval("DISCOUNT_OVER_LIMIT", manager.username(), TestData.PIN, "15.00");
        assertThat(token).startsWith("ag_");

        var consumed = tx.execute(s -> approvals.consume(token, ApprovalAction.DISCOUNT_OVER_LIMIT, storeId,
                cashier.id(), new BigDecimal("15.00")));
        assertThat(consumed.approverId()).isEqualTo(manager.id());
        assertThat(consumed.limitValue()).isEqualByComparingTo("20.00"); // MANAGER role limit

        assertThatThrownBy(() -> tx.execute(s -> approvals.consume(token, ApprovalAction.DISCOUNT_OVER_LIMIT, storeId,
                cashier.id(), new BigDecimal("15.00"))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.APPROVAL_INVALID);
    }

    @Test
    void approvalIsBoundToActionRequesterAndLimit() throws Exception {
        String token = requestApproval("DISCOUNT_OVER_LIMIT", manager.username(), TestData.PIN, "10.00");
        TestUser otherCashier = data.user("CASHIER", storeId);

        assertThatThrownBy(() -> tx.execute(s -> approvals.consume(token, ApprovalAction.PRICE_OVERRIDE, storeId,
                cashier.id(), null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> tx.execute(s -> approvals.consume(token, ApprovalAction.DISCOUNT_OVER_LIMIT, storeId,
                otherCashier.id(), new BigDecimal("10.00")))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> tx.execute(s -> approvals.consume(token, ApprovalAction.DISCOUNT_OVER_LIMIT, storeId,
                cashier.id(), new BigDecimal("25.00")))).isInstanceOf(ApiException.class);
    }

    @Test
    void discountAboveTheApproversOwnLimitIsRefused() throws Exception {
        cashierClient.perform(post("/api/v1/approvals").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"action\":\"DISCOUNT_OVER_LIMIT\",\"username\":\"%s\",\"secret\":\"%s\",\"value\":\"35.00\"}"
                                .formatted(manager.username(), TestData.PIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_INVALID"));
    }

    @Test
    void wrongSecretAndUnauthorisedApproverAreRefused() throws Exception {
        cashierClient.perform(post("/api/v1/approvals").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"action\":\"RETURN\",\"username\":\"%s\",\"secret\":\"0000\"}".formatted(manager.username())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_INVALID"));

        TestUser otherCashier = data.user("CASHIER", storeId);
        cashierClient.perform(post("/api/v1/approvals").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"action\":\"RETURN\",\"username\":\"%s\",\"secret\":\"%s\"}"
                                .formatted(otherCashier.username(), TestData.PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_INVALID"));
    }

    @Test
    void missingTokenMeansApprovalRequired() {
        assertThatThrownBy(() -> tx.execute(s -> approvals.consume(null, ApprovalAction.RETURN, storeId,
                cashier.id(), null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.APPROVAL_REQUIRED);
    }
}
