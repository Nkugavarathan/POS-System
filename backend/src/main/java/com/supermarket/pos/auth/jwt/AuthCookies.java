package com.supermarket.pos.auth.jwt;

import com.supermarket.pos.common.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * The two authentication cookies. Both are HttpOnly (unreadable by JavaScript) and
 * SameSite=Strict. The refresh cookie is only sent to /api/v1/auth, so it never travels
 * with ordinary API calls.
 */
@Component
public class AuthCookies {

    public static final String ACCESS_COOKIE = "POS_AT";
    public static final String REFRESH_COOKIE = "POS_RT";
    public static final String REFRESH_PATH = "/api/v1/auth";
    public static final String SHOP_ACCESS_COOKIE = "SHOP_AT";
    public static final String SHOP_REFRESH_COOKIE = "SHOP_RT";
    public static final String SHOP_REFRESH_PATH = "/api/v1/shop/auth";

    private final AppProperties properties;

    public AuthCookies(AppProperties properties) {
        this.properties = properties;
    }

    public void writeAccess(HttpHeaders headers, String token) {
        headers.add(HttpHeaders.SET_COOKIE,
                build(ACCESS_COOKIE, token, "/", properties.security().accessTokenTtl()).toString());
    }

    public void writeRefresh(HttpHeaders headers, String token) {
        headers.add(HttpHeaders.SET_COOKIE,
                build(REFRESH_COOKIE, token, REFRESH_PATH, properties.security().refreshTokenTtl()).toString());
    }

    public void clear(HttpHeaders headers) {
        headers.add(HttpHeaders.SET_COOKIE, build(ACCESS_COOKIE, "", "/", Duration.ZERO).toString());
        headers.add(HttpHeaders.SET_COOKIE, build(REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO).toString());
    }

    public void writeShopSession(HttpHeaders headers, String access, String refresh) {
        headers.add(HttpHeaders.SET_COOKIE, build(SHOP_ACCESS_COOKIE, access, "/", properties.security().accessTokenTtl()).toString());
        headers.add(HttpHeaders.SET_COOKIE, build(SHOP_REFRESH_COOKIE, refresh, SHOP_REFRESH_PATH,
                properties.security().refreshFamilyMaxLifetime()).toString());
    }

    public void clearShop(HttpHeaders headers) {
        headers.add(HttpHeaders.SET_COOKIE, build(SHOP_ACCESS_COOKIE, "", "/", Duration.ZERO).toString());
        headers.add(HttpHeaders.SET_COOKIE, build(SHOP_REFRESH_COOKIE, "", SHOP_REFRESH_PATH, Duration.ZERO).toString());
    }

    public Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.security().cookieSecure())
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
