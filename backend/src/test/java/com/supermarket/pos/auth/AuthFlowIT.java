package com.supermarket.pos.auth;

import com.supermarket.pos.auth.config.RateLimitFilter;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class AuthFlowIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestData data;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RateLimitFilter rateLimit;

    Long storeId;

    @BeforeEach
    void setUp() {
        rateLimit.reset();
        storeId = data.store("AUTH1");
    }

    @Test
    void loginSetsHttpOnlyCookiesAndReturnsTheUserWithoutTokens() throws Exception {
        TestUser cashier = data.user("CASHIER", storeId);
        ApiClient client = new ApiClient(mvc);

        client.login(cashier.username(), TestData.PASSWORD)
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("POS_AT", true))
                .andExpect(cookie().httpOnly("POS_RT", true))
                .andExpect(cookie().path("POS_RT", "/api/v1/auth"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.username").value(cashier.username()))
                .andExpect(jsonPath("$.permissions", hasItem("POS_SELL")))
                .andExpect(jsonPath("$.permissions", not(hasItem("USER_MANAGE"))))
                .andExpect(jsonPath("$.stores[0].id").value(storeId))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        client.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cashier.id()));
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameGenericError() throws Exception {
        TestUser cashier = data.user("CASHIER", storeId);
        new ApiClient(mvc).login(cashier.username(), "nope-wrong1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
        new ApiClient(mvc).login("no_such_user_xyz", "nope-wrong1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test
    void accountLocksAfterRepeatedFailures() throws Exception {
        TestUser cashier = data.user("CASHIER", storeId);
        for (int i = 0; i < 5; i++) {
            new ApiClient(mvc).login(cashier.username(), "wrong-pass" + i).andExpect(status().isUnauthorized());
        }
        // Even the correct password is refused while the lock is active.
        new ApiClient(mvc).login(cashier.username(), TestData.PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        Integer failed = jdbc.queryForObject("SELECT failed_login_count FROM users WHERE id = ?", Integer.class,
                cashier.id());
        assertThat(failed).isEqualTo(5);
    }

    @Test
    void disabledAccountCannotSignIn() throws Exception {
        TestUser cashier = data.user("CASHIER", storeId);
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", cashier.id());
        new ApiClient(mvc).login(cashier.username(), TestData.PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void stateChangingRequestsWithoutCsrfHeaderAreRejected() throws Exception {
        TestUser cashier = data.user("CASHIER", storeId);
        ApiClient client = new ApiClient(mvc).fetchCsrf();
        client.performWithoutCsrf(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + cashier.username() + "\",\"password\":\"" + TestData.PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void unauthenticatedApiCallsGetProblemDetails() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void refreshRotatesTheTokenAndReuseOutsideGraceRevokesTheFamily() throws Exception {
        TestUser cashier = data.user("CASHIER", storeId);
        ApiClient client = new ApiClient(mvc);
        client.login(cashier.username(), TestData.PASSWORD).andExpect(status().isOk());
        String firstRefresh = client.cookie("POS_RT");

        client.perform(post("/api/v1/auth/refresh")).andExpect(status().isOk());
        String secondRefresh = client.cookie("POS_RT");
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        // Simulate the old token being replayed by an attacker after the grace window.
        jdbc.update("UPDATE refresh_tokens SET revoked_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 HOUR) "
                + "WHERE user_id = ? AND revoke_reason = 'ROTATED'", cashier.id());
        ApiClient attacker = new ApiClient(mvc).fetchCsrf();
        attacker.setCookie("POS_RT", firstRefresh);
        attacker.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

        // The legitimate session is gone too: the family was revoked.
        client.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
        Integer reuseAudits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'SECURITY_TOKEN_REUSE' AND entity_id = ?",
                Integer.class, String.valueOf(cashier.id()));
        assertThat(reuseAudits).isEqualTo(1);
    }

    @Test
    void concurrentRefreshWithinGraceDoesNotKillTheSession() throws Exception {
        TestUser cashier = data.user("CASHIER", storeId);
        ApiClient tabA = new ApiClient(mvc);
        tabA.login(cashier.username(), TestData.PASSWORD).andExpect(status().isOk());
        String shared = tabA.cookie("POS_RT");

        tabA.perform(post("/api/v1/auth/refresh")).andExpect(status().isOk());
        // Tab B still holds the old cookie value for a moment:
        ApiClient tabB = new ApiClient(mvc).fetchCsrf();
        tabB.setCookie("POS_RT", shared);
        tabB.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
        // ...but tab A (the new token) still works.
        tabA.perform(post("/api/v1/auth/refresh")).andExpect(status().isOk());
    }

    @Test
    void logoutRevokesTheSession() throws Exception {
        TestUser cashier = data.user("CASHIER", storeId);
        ApiClient client = new ApiClient(mvc);
        client.login(cashier.username(), TestData.PASSWORD).andExpect(status().isOk());
        String refresh = client.cookie("POS_RT");

        client.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
        assertThat(client.cookie("POS_AT")).isNull();

        ApiClient replay = new ApiClient(mvc).fetchCsrf();
        replay.setCookie("POS_RT", refresh);
        replay.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void temporaryPasswordMustBeChangedBeforeUsingTheApi() throws Exception {
        TestUser cashier = data.user("CASHIER", storeId);
        jdbc.update("UPDATE users SET must_change_password = TRUE WHERE id = ?", cashier.id());
        ApiClient client = new ApiClient(mvc);
        client.login(cashier.username(), TestData.PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        client.perform(get("/api/v1/stores"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        client.perform(post("/api/v1/auth/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + TestData.PASSWORD + "\",\"newPassword\":\"BrandNew9x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        client.perform(get("/api/v1/stores")).andExpect(status().isOk());
    }

    @Test
    void permissionsAreEnforcedServerSide() throws Exception {
        TestUser cashier = data.user("CASHIER", storeId);
        TestUser admin = data.user("ADMIN", storeId);

        ApiClient cashierClient = new ApiClient(mvc);
        cashierClient.login(cashier.username(), TestData.PASSWORD);
        cashierClient.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        ApiClient adminClient = new ApiClient(mvc);
        adminClient.login(admin.username(), TestData.PASSWORD);
        adminClient.perform(get("/api/v1/users")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void storeIsolationHidesOtherStores() throws Exception {
        Long otherStore = data.store("AUTH2");
        TestUser cashier = data.user("CASHIER", storeId);
        ApiClient client = new ApiClient(mvc);
        client.login(cashier.username(), TestData.PASSWORD);

        client.perform(get("/api/v1/stores/" + otherStore))
                .andExpect(status().isNotFound());
        client.perform(get("/api/v1/stores/" + storeId + "/registers").header("X-Store-Id", otherStore))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotGrantSuperAdmin() throws Exception {
        TestUser admin = data.user("ADMIN", storeId);
        ApiClient client = new ApiClient(mvc);
        client.login(admin.username(), TestData.PASSWORD);
        client.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"escalate_me","fullName":"X","temporaryPassword":"Temp1234x",
                         "roles":["SUPER_ADMIN"],"storeIds":[%d]}""".formatted(storeId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void validationErrorsListFieldsWithoutEchoingPasswords() throws Exception {
        TestUser admin = data.user("ADMIN", storeId);
        ApiClient client = new ApiClient(mvc);
        client.login(admin.username(), TestData.PASSWORD);
        client.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"x","fullName":"","temporaryPassword":"short","roles":[],"storeIds":[]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='username')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='temporaryPassword')]").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='temporaryPassword')].rejectedValue").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));
    }
}
