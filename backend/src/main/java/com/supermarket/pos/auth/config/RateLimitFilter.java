package com.supermarket.pos.auth.config;

import com.supermarket.pos.common.error.ErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Per-client-IP token buckets for credential-handling endpoints (login, refresh, approvals).
 * Complements the per-account lockout in the auth service. In-memory: limits apply per
 * backend instance (see docs/01-architecture.md §11 for scale-out).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String path, int capacity, Duration period) {
    }

    private static final Map<String, Rule> RULES = Map.of(
            "/api/v1/auth/login", new Rule("/api/v1/auth/login", 10, Duration.ofMinutes(1)),
            "/api/v1/auth/refresh", new Rule("/api/v1/auth/refresh", 60, Duration.ofMinutes(1)),
            "/api/v1/approvals", new Rule("/api/v1/approvals", 10, Duration.ofMinutes(1)));

    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ProblemResponseWriter problems;

    public RateLimitFilter(ProblemResponseWriter problems) {
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !RULES.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        Rule rule = RULES.get(request.getRequestURI());
        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            buckets.clear(); // crude bound on memory; buckets refill anyway
        }
        Bucket bucket = buckets.computeIfAbsent(rule.path() + "|" + request.getRemoteAddr(), k -> newBucket(rule));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        problems.write(request, response, ErrorCode.RATE_LIMITED,
                "Too many attempts. Try again in " + retryAfter + " seconds.");
    }

    /** Clears all buckets (used by tests that sign in many times). */
    public void reset() {
        buckets.clear();
    }

    private static Bucket newBucket(Rule rule) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(rule.capacity())
                        .refillGreedy(rule.capacity(), rule.period()).build())
                .build();
    }
}
