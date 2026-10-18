package com.supermarket.pos.auth.service;

import com.supermarket.pos.audit.entity.AuditOutcome;
import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.auth.dto.AuthDtos.AuthUserDto;
import com.supermarket.pos.auth.dto.AuthDtos.ChangePasswordRequest;
import com.supermarket.pos.auth.dto.AuthDtos.ClientInfo;
import com.supermarket.pos.auth.dto.AuthDtos.LoginRequest;
import com.supermarket.pos.auth.service.SessionService.IssuedSession;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.security.CurrentUser;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.store.dto.StoreDtos.StoreSummaryDto;
import com.supermarket.pos.store.service.StoreService;
import com.supermarket.pos.users.entity.User;
import com.supermarket.pos.users.entity.UserStatus;
import com.supermarket.pos.users.service.UserAccountService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Login, refresh, logout and self-service account operations. Error messages never reveal
 * whether a username exists; failed attempts count toward a temporary lockout.
 */
@Service
public class AuthService {

    private final UserAccountService accounts;
    private final SessionService sessions;
    private final StoreService stores;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;
    private final Clock clock;

    public AuthService(UserAccountService accounts, SessionService sessions, StoreService stores,
                       CurrentUserProvider currentUser, AuditService audit, Clock clock) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.stores = stores;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    /** Result of a successful login or refresh: the cookie values and the user DTO. */
    public record SessionResult(AuthUserDto user, String accessToken, String refreshToken) {
    }

    /** Failure bookkeeping (counter, audit) must commit even though the request fails. */
    @Transactional(noRollbackFor = ApiException.class)
    public SessionResult login(LoginRequest request, ClientInfo client) {
        Instant now = clock.instant();
        String username = request.username().trim();
        Optional<User> found = accounts.findForAuthentication(username);
        if (found.isEmpty()) {
            accounts.simulatePasswordCheck(request.password());
            auditLogin("LOGIN_FAILED", null, username, AuditOutcome.FAILURE, "Unknown username");
            throw invalidCredentials();
        }
        User user = found.get();
        if (user.getStatus() == UserStatus.LOCKED || user.isTemporarilyLocked(now)) {
            auditLogin("LOGIN_FAILED", user.getId(), user.getUsername(), AuditOutcome.DENIED, "Account locked");
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED,
                    "This account is locked. Try again later or contact a manager.");
        }
        if (!accounts.passwordMatches(user, request.password())) {
            accounts.registerFailedLogin(user);
            auditLogin("LOGIN_FAILED", user.getId(), user.getUsername(), AuditOutcome.FAILURE, "Wrong password");
            throw invalidCredentials();
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            auditLogin("LOGIN_FAILED", user.getId(), user.getUsername(), AuditOutcome.DENIED, "Account disabled");
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "This account is disabled.");
        }
        if (user.storeIds().isEmpty()) {
            auditLogin("LOGIN_FAILED", user.getId(), user.getUsername(), AuditOutcome.DENIED, "No store assigned");
            throw ApiException.forbidden("Your account is not assigned to any store.");
        }
        accounts.registerSuccessfulLogin(user);
        IssuedSession session = sessions.start(user, client);
        auditLogin("LOGIN_SUCCEEDED", user.getId(), user.getUsername(), AuditOutcome.SUCCESS, "Signed in");
        return toResult(session);
    }

    public SessionResult refresh(String rawRefreshToken, ClientInfo client) {
        return toResult(sessions.rotate(rawRefreshToken, client));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        sessions.end(rawRefreshToken).ifPresent(userId ->
                auditLogin("LOGOUT", userId, null, AuditOutcome.SUCCESS, "Signed out"));
    }

    @Transactional(readOnly = true)
    public AuthUserDto me() {
        CurrentUser current = currentUser.require();
        User user = accounts.loadForSession(current.id());
        return toDto(user, tokenExpiry());
    }

    /** Changes the password, ends every other session and starts a fresh one for this browser. */
    @Transactional
    public SessionResult changePassword(ChangePasswordRequest request, ClientInfo client) {
        CurrentUser current = currentUser.require();
        accounts.changeOwnPassword(current.id(), request.currentPassword(), request.newPassword());
        audit.record(AuditService.Entry.success("PASSWORD_CHANGED", "User", current.id(), "Password changed"));
        User user = accounts.loadForSession(current.id());
        return toResult(sessions.start(user, client));
    }

    private SessionResult toResult(IssuedSession session) {
        return new SessionResult(toDto(session.user(), session.accessTokenExpiresAt()),
                session.accessToken(), session.refreshToken());
    }

    private AuthUserDto toDto(User user, Instant accessTokenExpiresAt) {
        Map<Long, StoreSummaryDto> summaries = stores.summaries(user.storeIds());
        List<StoreSummaryDto> storeList = summaries.values().stream()
                .sorted(Comparator.comparing(StoreSummaryDto::name)).toList();
        return new AuthUserDto(user.getId(), user.getUsername(), user.getFullName(),
                user.roleCodes().stream().sorted().toList(),
                user.permissionCodes().stream().sorted().toList(),
                storeList, user.defaultStoreId().orElse(null), user.maxDiscountPercent(),
                user.isMustChangePassword(), accessTokenExpiresAt);
    }

    private static Instant tokenExpiry() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            return token.getToken().getExpiresAt();
        }
        return null;
    }

    private void auditLogin(String action, Long userId, String username, AuditOutcome outcome, String summary) {
        audit.record(new AuditService.Entry(action, "User", userId, outcome, summary, null, null, userId,
                username));
    }

    private static ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password.");
    }
}
