package com.supermarket.pos.auth.service;

import com.supermarket.pos.audit.entity.AuditOutcome;
import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.auth.dto.AuthDtos.ClientInfo;
import com.supermarket.pos.auth.entity.RefreshToken;
import com.supermarket.pos.auth.jwt.JwtService;
import com.supermarket.pos.auth.jwt.JwtService.AccessTokenSubject;
import com.supermarket.pos.auth.jwt.JwtService.IssuedToken;
import com.supermarket.pos.auth.repository.RefreshTokenRepository;
import com.supermarket.pos.common.config.AppProperties;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.users.entity.User;
import com.supermarket.pos.users.entity.UserStatus;
import com.supermarket.pos.users.service.UserAccountService;
import com.supermarket.pos.users.service.UserEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh-token families. Each login starts a family; each refresh rotates the token.
 * Presenting a token that was already rotated means it was copied: the whole family is
 * revoked (unless the reuse happens within a short grace window, which is the normal
 * outcome of two browser tabs refreshing at the same moment).
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    static final Duration ROTATION_GRACE = Duration.ofSeconds(20);

    private final RefreshTokenRepository tokens;
    private final UserAccountService accounts;
    private final JwtService jwt;
    private final AppProperties properties;
    private final AuditService audit;
    private final Clock clock;

    public SessionService(RefreshTokenRepository tokens, UserAccountService accounts, JwtService jwt,
                          AppProperties properties, AuditService audit, Clock clock) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.jwt = jwt;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    /** Raw tokens to put into cookies, plus the user they belong to. */
    public record IssuedSession(User user, String accessToken, Instant accessTokenExpiresAt, String refreshToken) {
    }

    @Transactional
    public IssuedSession start(User user, ClientInfo client) {
        Instant now = clock.instant();
        String familyId = UUID.randomUUID().toString();
        Instant familyExpiry = now.plus(properties.security().refreshFamilyMaxLifetime());
        String raw = SecureTokens.newToken("rt_");
        tokens.save(newToken(user.getId(), raw, familyId, now, familyExpiry, client));
        return issue(user, familyId, raw);
    }

    /** Rotation. Commits revocations even when it ends with an error (reuse detection). */
    @Transactional(noRollbackFor = ApiException.class)
    public IssuedSession rotate(String rawToken, ClientInfo client) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalid();
        }
        Instant now = clock.instant();
        RefreshToken current = tokens.findByHashForUpdate(SecureTokens.sha256(rawToken)).orElseThrow(SessionService::invalid);

        if (current.getReplacedById() != null) {
            boolean benignRace = RefreshToken.REASON_ROTATED.equals(current.getRevokeReason())
                    && current.getRevokedAt() != null
                    && Duration.between(current.getRevokedAt(), now).compareTo(ROTATION_GRACE) < 0;
            if (!benignRace) {
                int revoked = tokens.revokeFamily(current.getFamilyId(), now, RefreshToken.REASON_REUSE);
                log.warn("Refresh token reuse detected for user {} (family {}), revoked {} tokens",
                        current.getUserId(), current.getFamilyId(), revoked);
                audit.record(new AuditService.Entry("SECURITY_TOKEN_REUSE", "User", current.getUserId(),
                        AuditOutcome.DENIED, "Rotated refresh token presented again; session family revoked",
                        Map.of("ip", String.valueOf(client.ipAddress())), null, current.getUserId(), null));
            }
            throw invalid();
        }
        if (!current.isActive(now)) {
            throw invalid();
        }
        User user = accounts.loadForSession(current.getUserId());
        if (user.getStatus() != UserStatus.ACTIVE || user.isTemporarilyLocked(now)) {
            tokens.revokeFamily(current.getFamilyId(), now, RefreshToken.REASON_USER_DISABLED);
            throw invalid();
        }

        String raw = SecureTokens.newToken("rt_");
        RefreshToken next = tokens.save(newToken(user.getId(), raw, current.getFamilyId(), now,
                current.getFamilyExpiresAt(), client));
        current.setRevokedAt(now);
        current.setRevokeReason(RefreshToken.REASON_ROTATED);
        current.setReplacedById(next.getId());
        return issue(user, current.getFamilyId(), raw);
    }

    /** Ends the session the token belongs to. Unknown tokens are ignored (logout is idempotent). */
    @Transactional
    public Optional<Long> end(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(SecureTokens.sha256(rawToken)).map(token -> {
            tokens.revokeFamily(token.getFamilyId(), clock.instant(), RefreshToken.REASON_LOGOUT);
            return token.getUserId();
        });
    }

    @Transactional
    public void revokeAll(Long userId, String reason) {
        int count = tokens.revokeAllForUser(userId, clock.instant(), truncate(reason));
        log.info("Revoked {} refresh tokens of user {} ({})", count, userId, reason);
    }

    /** Disabling a user, resetting a password or changing roles ends every session of that user. */
    @EventListener
    public void onSessionsInvalidated(UserEvents.SessionsInvalidated event) {
        revokeAll(event.userId(), event.reason());
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpired() {
        int removed = tokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
        if (removed > 0) {
            log.info("Purged {} expired refresh tokens", removed);
        }
    }

    /** Builds a fresh access token (current roles and permissions) for an existing family. */
    public IssuedSession issue(User user, String familyId, String rawRefreshToken) {
        IssuedToken access = jwt.issueAccessToken(new AccessTokenSubject(
                user.getId(), user.getUsername(), user.getFullName(), user.roleCodes(), user.permissionCodes(),
                user.storeIds(), user.defaultStoreId().orElse(null), familyId, user.isMustChangePassword()));
        return new IssuedSession(user, access.value(), access.expiresAt(), rawRefreshToken);
    }

    private RefreshToken newToken(Long userId, String raw, String familyId, Instant now, Instant familyExpiry,
                                  ClientInfo client) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(SecureTokens.sha256(raw));
        token.setFamilyId(familyId);
        token.setIssuedAt(now);
        Instant expiry = now.plus(properties.security().refreshTokenTtl());
        token.setExpiresAt(expiry.isBefore(familyExpiry) ? expiry : familyExpiry);
        token.setFamilyExpiresAt(familyExpiry);
        token.setIpAddress(client == null ? null : truncate(client.ipAddress(), 45));
        token.setUserAgent(client == null ? null : truncate(client.userAgent(), 255));
        return token;
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "Your session has ended. Please sign in again.");
    }

    private static String truncate(String value) {
        return truncate(value, 30);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
