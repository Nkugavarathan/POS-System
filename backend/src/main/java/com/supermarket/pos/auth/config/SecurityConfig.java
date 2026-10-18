package com.supermarket.pos.auth.config;

import com.supermarket.pos.auth.jwt.AuthCookies;
import com.supermarket.pos.auth.jwt.CookieBearerTokenResolver;
import com.supermarket.pos.auth.jwt.JwtService;
import com.supermarket.pos.common.config.AppProperties;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.security.JwtClaims;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Stateless API security:
 * <ul>
 *   <li>JWT access token from the HttpOnly cookie (or a Bearer header for API clients);</li>
 *   <li>CSRF double-submit cookie for cookie-authenticated browser requests;</li>
 *   <li>authorities = permission codes (perms claim) + ROLE_* (roles claim);</li>
 *   <li>method security for per-endpoint permissions; services re-check data-dependent rules.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_DOCS = {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, JwtDecoder jwtDecoder, AuthCookies cookies,
                                           AppProperties properties, ProblemResponseWriter problems,
                                           RateLimitFilter rateLimitFilter,
                                           PasswordChangeRequiredFilter passwordChangeFilter) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(c -> c.sameSite("Strict").secure(properties.security().cookieSecure()));

        http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // Header-authenticated API clients do not use cookies, so CSRF cannot apply to them.
                        .ignoringRequestMatchers(r -> r.getHeader(HttpHeaders.AUTHORIZATION) != null)
                        .ignoringRequestMatchers(EndpointRequest.toAnyEndpoint()))
                .headers(h -> h
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(c -> c.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; object-src 'none'")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to("health", "info", "prometheus")).permitAll()
                        .requestMatchers(EndpointRequest.toAnyEndpoint()).hasAuthority("SYSTEM_CONFIG")
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(PUBLIC_DOCS).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs
                        .bearerTokenResolver(new CookieBearerTokenResolver(cookies))
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(authenticationConverter()))
                        .authenticationEntryPoint((request, response, ex) -> problems.write(request, response,
                                isExpired(ex) ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHENTICATED,
                                isExpired(ex) ? "The access token has expired." : "Authentication required."))
                        .accessDeniedHandler((request, response, ex) -> problems.write(request, response,
                                ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action.")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) -> problems.write(request, response,
                                ErrorCode.UNAUTHENTICATED, "Authentication required."))
                        .accessDeniedHandler((request, response, ex) -> {
                            if (ex instanceof CsrfException) {
                                problems.write(request, response, ErrorCode.CSRF_INVALID,
                                        "Missing or invalid CSRF token. Reload the page and try again.");
                            } else {
                                problems.write(request, response, ErrorCode.ACCESS_DENIED,
                                        "You do not have permission to perform this action.");
                            }
                        }))
                .addFilterBefore(rateLimitFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(passwordChangeFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtService jwtService) {
        return jwtService.decoder();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    static Converter<Jwt, AbstractAuthenticationToken> authenticationConverter() {
        return jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            claimList(jwt, JwtClaims.PERMISSIONS).forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
            claimList(jwt, JwtClaims.ROLES).forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString(JwtClaims.USERNAME));
        };
    }

    private static Collection<String> claimList(Jwt jwt, String claim) {
        List<String> values = jwt.getClaimAsStringList(claim);
        return values == null ? List.of() : values;
    }

    private static boolean isExpired(Exception ex) {
        if (ex instanceof OAuth2AuthenticationException oauth && oauth.getError().getDescription() != null) {
            return oauth.getError().getDescription().toLowerCase(Locale.ROOT).contains("expired");
        }
        return false;
    }

    private static CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Idempotency-Key", "X-Correlation-Id",
                "X-Store-Id", "Authorization"));
        config.setExposedHeaders(List.of("X-Correlation-Id", "Idempotent-Replayed", "Content-Disposition"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
