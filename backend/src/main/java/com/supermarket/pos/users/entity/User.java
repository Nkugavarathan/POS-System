package com.supermarket.pos.users.entity;

import com.supermarket.pos.common.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(length = 120)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** Optional BCrypt-hashed approval PIN for managers. */
    @Column(name = "pin_hash", length = 100)
    private String pinHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Version
    private long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserStoreAccess> storeAccess = new HashSet<>();

    public Set<String> roleCodes() {
        return roles.stream().map(Role::getCode).collect(Collectors.toSet());
    }

    public Set<String> permissionCodes() {
        return roles.stream().flatMap(r -> r.getPermissions().stream()).map(Permission::getCode)
                .collect(Collectors.toSet());
    }

    public Set<Long> storeIds() {
        return storeAccess.stream().map(UserStoreAccess::getStoreId).collect(Collectors.toSet());
    }

    public Optional<Long> defaultStoreId() {
        return storeAccess.stream().filter(UserStoreAccess::isDefault).map(UserStoreAccess::getStoreId).findFirst()
                .or(() -> storeAccess.stream().map(UserStoreAccess::getStoreId).sorted().findFirst());
    }

    /** The highest discount percent across the user's roles. */
    public BigDecimal maxDiscountPercent() {
        return roles.stream().map(Role::getMaxDiscountPercent).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    public boolean isTemporarilyLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void grantStore(Long storeId, boolean isDefault) {
        if (storeAccess.stream().noneMatch(a -> a.getStoreId().equals(storeId))) {
            storeAccess.add(new UserStoreAccess(this, storeId, isDefault));
        }
    }
}
