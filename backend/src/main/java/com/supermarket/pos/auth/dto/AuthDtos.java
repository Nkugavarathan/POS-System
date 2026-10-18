package com.supermarket.pos.auth.dto;

import com.supermarket.pos.auth.entity.ApprovalAction;
import com.supermarket.pos.common.validation.StrongPassword;
import com.supermarket.pos.store.dto.StoreDtos.StoreSummaryDto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Size(max = 50) String username,
                               @NotBlank @Size(max = 128) String password) {
    }

    public record AuthUserDto(Long id, String username, String fullName, List<String> roles, List<String> permissions,
                              List<StoreSummaryDto> stores, Long defaultStoreId, BigDecimal maxDiscountPercent,
                              boolean mustChangePassword, Instant accessTokenExpiresAt) {
    }

    public record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
                                        @StrongPassword String newPassword) {
    }

    public record ApprovalRequest(@NotNull ApprovalAction action,
                                  @NotBlank @Size(max = 50) String username,
                                  @NotBlank @Size(max = 128) String secret,
                                  Long registerId,
                                  @DecimalMin("0.00") BigDecimal value) {
    }

    public record ApproverDto(Long id, String fullName) {
    }

    public record ApprovalGrantDto(String approvalToken, ApprovalAction action, ApproverDto approver,
                                   BigDecimal limitValue, Instant expiresAt) {
    }

    /** Client network details recorded with sessions and audit entries. */
    public record ClientInfo(String ipAddress, String userAgent) {
    }
}
