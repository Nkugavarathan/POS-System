package com.supermarket.pos.auth.config;

import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.security.JwtClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Users created or reset by an administrator carry a temporary password. Until they set
 * their own, every API call except the session and password-change endpoints is refused.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of(
            "/api/v1/auth/me", "/api/v1/auth/me/password", "/api/v1/auth/logout",
            "/api/v1/auth/refresh", "/api/v1/auth/csrf");

    private final ProblemResponseWriter problems;

    public PasswordChangeRequiredFilter(ProblemResponseWriter problems) {
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token
                && Boolean.TRUE.equals(token.getToken().getClaimAsBoolean(JwtClaims.MUST_CHANGE_PASSWORD))
                && request.getRequestURI().startsWith("/api/")
                && !ALLOWED.contains(request.getRequestURI())) {
            problems.write(request, response, ErrorCode.PASSWORD_CHANGE_REQUIRED,
                    "You must change your temporary password before continuing.");
            return;
        }
        chain.doFilter(request, response);
    }
}
