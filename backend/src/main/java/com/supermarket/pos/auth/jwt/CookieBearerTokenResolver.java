package com.supermarket.pos.auth.jwt;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Reads the access token from the HttpOnly {@code POS_AT} cookie (browser clients). Falls
 * back to the standard {@code Authorization: Bearer} header for non-browser API clients,
 * which are not exposed to CSRF because they do not use cookies.
 *
 * <p>The session endpoints (login, refresh, logout, csrf) ignore the cookie: they must work
 * even when an expired access cookie is still present.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
    private final AuthCookies cookies;

    public CookieBearerTokenResolver(AuthCookies cookies) {
        this.cookies = cookies;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String fromHeader = headerResolver.resolve(request);
        if (fromHeader != null) {
            return fromHeader;
        }
        String path = request.getRequestURI();
        if (isSessionEndpoint(path) || path.startsWith("/api/v1/shop/public/")) {
            return null;
        }
        // The online shop and the back office use separate cookies, so a browser signed in to both
        // never sends a staff token to the shop (or the other way round).
        String cookie = path.startsWith("/api/v1/shop/") ? AuthCookies.SHOP_ACCESS_COOKIE : AuthCookies.ACCESS_COOKIE;
        return cookies.read(request, cookie).orElse(null);
    }

    public static boolean isSessionEndpoint(String path) {
        return path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/refresh")
                || path.equals("/api/v1/auth/logout") || path.equals("/api/v1/auth/csrf")
                || path.equals("/api/v1/shop/auth/login") || path.equals("/api/v1/shop/auth/register")
                || path.equals("/api/v1/shop/auth/refresh") || path.equals("/api/v1/shop/auth/logout");
    }
}
