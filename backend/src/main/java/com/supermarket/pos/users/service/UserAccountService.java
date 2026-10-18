package com.supermarket.pos.users.service;

import com.supermarket.pos.common.config.AppProperties;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.users.entity.User;
import com.supermarket.pos.users.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Account operations used by the auth module (credential checks, lockout counters,
 * self-service password change). Administration lives in {@link UserService}.
 */
@Service
public class UserAccountService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    /** Real hash of a random value, used only to equalize timing for unknown usernames. */
    private final String dummyHash;

    public UserAccountService(UserRepository users, PasswordEncoder passwordEncoder, AppProperties properties,
                              ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    /** User with roles, permissions and store access loaded. */
    @Transactional(readOnly = true)
    public Optional<User> findForAuthentication(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return users.findWithAuthoritiesByUsernameIgnoreCase(username.trim());
    }

    /** user id → full name, for showing who did what (receipts, shift lists, audit). */
    @Transactional(readOnly = true)
    public java.util.Map<Long, String> displayNames(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Map.of();
        }
        return users.findAllById(new java.util.HashSet<>(ids)).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, User::getFullName));
    }

    /** Highest manual-discount percent the user may give without approval (from their roles). */
    @Transactional(readOnly = true)
    public java.math.BigDecimal maxDiscountPercent(Long userId) {
        return users.findWithAuthoritiesById(userId).map(User::maxDiscountPercent).orElse(java.math.BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public User loadForSession(Long userId) {
        return users.findWithAuthoritiesById(userId).orElseThrow(() -> ApiException.notFound("User", userId));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void registerFailedLogin(User user) {
        Instant lockUntil = clock.instant().plus(properties.security().lockoutDuration());
        users.registerFailedLogin(user.getId(), properties.security().maxFailedLogins(), lockUntil);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void registerSuccessfulLogin(User user) {
        users.registerSuccessfulLogin(user.getId(), clock.instant());
    }

    public boolean passwordMatches(User user, String rawPassword) {
        return rawPassword != null && passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    /** Approval secret: the PIN when one is set, otherwise the password. */
    public boolean approvalSecretMatches(User user, String secret) {
        if (secret == null) {
            return false;
        }
        if (user.getPinHash() != null && passwordEncoder.matches(secret, user.getPinHash())) {
            return true;
        }
        return passwordEncoder.matches(secret, user.getPasswordHash());
    }

    /** Runs a BCrypt comparison anyway, so unknown usernames take as long as wrong passwords. */
    public void simulatePasswordCheck(String rawPassword) {
        passwordEncoder.matches(rawPassword == null ? "" : rawPassword, dummyHash);
    }

    @Transactional
    public void changeOwnPassword(Long userId, String currentPassword, String newPassword) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User", userId));
        if (!passwordMatches(user, currentPassword)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "The current password is incorrect.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.rule("The new password must be different from the current one.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(clock.instant());
        user.setMustChangePassword(false);
        events.publishEvent(new UserEvents.SessionsInvalidated(userId, "PASSWORD_CHANGED"));
    }
}
