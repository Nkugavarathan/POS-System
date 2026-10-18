package com.supermarket.pos.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/** Typed, validated application settings ({@code app.*}). Startup fails if required values are missing. */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@Valid @NotNull Security security,
                            @Valid @NotNull Cors cors,
                            @Valid @NotNull Idempotency idempotency) {

    public record Security(@NotBlank String jwtSecret,
                           @NotNull Duration accessTokenTtl,
                           @NotNull Duration refreshTokenTtl,
                           @NotNull Duration refreshFamilyMaxLifetime,
                           @NotNull Duration approvalTokenTtl,
                           boolean cookieSecure,
                           @Min(1) int maxFailedLogins,
                           @NotNull Duration lockoutDuration) {
    }

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of()
                    : allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    public record Idempotency(@NotNull Duration retention) {
    }
}
