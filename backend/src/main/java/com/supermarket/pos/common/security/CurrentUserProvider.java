package com.supermarket.pos.common.security;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Resolves the {@link CurrentUser} for the current request from the JWT principal. */
@Component
public class CurrentUserProvider {

    public static final String STORE_HEADER = "X-Store-Id";

    public Optional<CurrentUser> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token) || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        Jwt jwt = token.getToken();
        Set<Long> stores = longSet(jwt.getClaim(JwtClaims.STORES));
        Long defaultStore = toLong(jwt.getClaim(JwtClaims.DEFAULT_STORE));
        return Optional.of(new CurrentUser(
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString(JwtClaims.USERNAME),
                jwt.getClaimAsString(JwtClaims.FULL_NAME),
                stringSet(jwt.getClaim(JwtClaims.ROLES)),
                stringSet(jwt.getClaim(JwtClaims.PERMISSIONS)),
                stores,
                resolveActiveStore(stores, defaultStore)));
    }

    public CurrentUser require() {
        return current().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required."));
    }

    private static Long resolveActiveStore(Set<Long> stores, Long defaultStore) {
        String header = currentRequest().map(r -> r.getHeader(STORE_HEADER)).orElse(null);
        if (header != null && !header.isBlank()) {
            Long requested;
            try {
                requested = Long.valueOf(header.trim());
            } catch (NumberFormatException e) {
                throw new ApiException(ErrorCode.MALFORMED_REQUEST, "X-Store-Id must be a number.");
            }
            if (!stores.contains(requested)) {
                throw ApiException.forbidden("You do not have access to this store.");
            }
            return requested;
        }
        if (defaultStore != null && stores.contains(defaultStore)) {
            return defaultStore;
        }
        return stores.stream().sorted().findFirst().orElse(null);
    }

    private static Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return Optional.of(attrs.getRequest());
        }
        return Optional.empty();
    }

    private static Set<String> stringSet(Object claim) {
        if (claim instanceof Collection<?> c) {
            return c.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }

    private static Set<Long> longSet(Object claim) {
        if (claim instanceof Collection<?> c) {
            return c.stream().map(CurrentUserProvider::toLong).filter(java.util.Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }

    private static Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            return Long.valueOf(s);
        }
        return null;
    }
}
