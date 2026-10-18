package com.supermarket.pos.users.dto;

import com.supermarket.pos.common.validation.StrongPassword;
import com.supermarket.pos.users.entity.UserStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Request and response DTOs of the users module. */
public final class UserDtos {

    private UserDtos() {
    }

    public record StoreRef(Long id, String code, String name, boolean isDefault) {
    }

    public record UserSummaryDto(Long id, String username, String fullName, String email, UserStatus status,
                                 List<String> roles, List<Long> storeIds, Instant lastLoginAt, Instant createdAt) {
    }

    public record UserDetailDto(Long id, String username, String fullName, String email, UserStatus status,
                                List<String> roles, List<StoreRef> stores, boolean hasApprovalPin,
                                boolean mustChangePassword, boolean temporarilyLocked, Instant lastLoginAt,
                                Instant createdAt, Instant updatedAt, long version) {
    }

    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$",
                    message = "3-50 letters, digits, dots, underscores or dashes") String username,
            @NotBlank @Size(max = 120) String fullName,
            @Email @Size(max = 120) String email,
            @StrongPassword String temporaryPassword,
            @NotEmpty Set<@NotBlank String> roles,
            @NotEmpty Set<@NotNull Long> storeIds,
            Long defaultStoreId) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Email @Size(max = 120) String email,
            @NotEmpty Set<@NotBlank String> roles,
            @NotEmpty Set<@NotNull Long> storeIds,
            Long defaultStoreId,
            @NotNull Long version) {
    }

    public record UserStatusRequest(@NotNull UserStatus status, @NotNull Long version) {
    }

    public record PasswordResetRequest(@StrongPassword String temporaryPassword) {
    }

    public record SetPinRequest(@NotBlank @Pattern(regexp = "^\\d{4,6}$", message = "must be 4-6 digits") String pin) {
    }

    public record RoleDto(Long id, String code, String name, String description, BigDecimal maxDiscountPercent,
                          boolean systemRole, List<String> permissions) {
    }

    public record UpdateRoleRequest(
            @NotNull Set<@NotBlank String> permissions,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal maxDiscountPercent) {
    }

    public record PermissionDto(String code, String module, String description) {
    }
}
