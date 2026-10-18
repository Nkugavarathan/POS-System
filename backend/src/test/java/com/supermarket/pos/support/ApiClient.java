package com.supermarket.pos.support;

import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A tiny browser emulation for MockMvc: keeps cookies between requests and sends the
 * X-XSRF-TOKEN header exactly like the frontend's Axios instance does.
 */
public class ApiClient {

    private final MockMvc mvc;
    private final Map<String, String> cookies = new HashMap<>();

    public ApiClient(MockMvc mvc) {
        this.mvc = mvc;
    }

    public ApiClient fetchCsrf() throws Exception {
        perform(get("/api/v1/auth/csrf"));
        return this;
    }

    public ResultActions login(String username, String password) throws Exception {
        if (!cookies.containsKey("XSRF-TOKEN")) {
            fetchCsrf();
        }
        return perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    public ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        if (!cookies.isEmpty()) {
            request.cookie(cookies.entrySet().stream().map(e -> new Cookie(e.getKey(), e.getValue()))
                    .toArray(Cookie[]::new));
        }
        String xsrf = cookies.get("XSRF-TOKEN");
        if (xsrf != null) {
            request.header("X-XSRF-TOKEN", xsrf);
        }
        ResultActions result = mvc.perform(request);
        absorb(result.andReturn().getResponse());
        return result;
    }

    /** Sends the request without the CSRF header (to prove CSRF protection). */
    public ResultActions performWithoutCsrf(MockHttpServletRequestBuilder request) throws Exception {
        if (!cookies.isEmpty()) {
            request.cookie(cookies.entrySet().stream().map(e -> new Cookie(e.getKey(), e.getValue()))
                    .toArray(Cookie[]::new));
        }
        return mvc.perform(request);
    }

    public String cookie(String name) {
        return cookies.get(name);
    }

    public void setCookie(String name, String value) {
        cookies.put(name, value);
    }

    private void absorb(MockHttpServletResponse response) {
        for (Cookie c : response.getCookies()) {
            if (c.getMaxAge() == 0 || c.getValue() == null || c.getValue().isEmpty()) {
                cookies.remove(c.getName());
            } else {
                cookies.put(c.getName(), c.getValue());
            }
        }
    }
}
