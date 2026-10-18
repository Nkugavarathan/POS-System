package com.supermarket.pos.customers.dto;

import com.supermarket.pos.customers.entity.LoyaltyTransaction;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    /** List row: the phone is masked (e.g. +94 *** *** 4567) to limit exposure on shared screens. */
    public record CustomerSummaryDto(Long id, String customerCode, String fullName, String maskedPhone, String email,
                                     String cardNumber, long pointsBalance, boolean active) {
    }

    public record CustomerDto(Long id, String customerCode, String fullName, String phone, String email,
                              boolean marketingConsent, boolean active, LoyaltyDto loyalty, boolean hasOnlineAccount,
                              Instant createdAt, long version) {
    }

    public record LoyaltyDto(String cardNumber, long pointsBalance, BigDecimal pointsValue, String status) {
    }

    /** Minimal data the cashier sees after scanning a loyalty card or typing a phone number. */
    public record PosCustomerDto(Long id, String fullName, String maskedPhone, String cardNumber, long pointsBalance,
                                 BigDecimal pointsValue) {
    }

    public record CustomerRequest(@NotBlank @Size(max = 120) String fullName,
                                  @Size(max = 20) String phone,
                                  @Email @Size(max = 120) String email,
                                  boolean marketingConsent,
                                  boolean enrolLoyalty,
                                  Long version) {
    }

    public record LoyaltyAdjustmentRequest(@NotNull Long points, @NotBlank @Size(max = 255) String reason) {
    }

    public record LoyaltyTransactionDto(Long id, LoyaltyTransaction.Type type, long points, long balanceAfter,
                                        String referenceType, Long referenceId, String reason, Instant createdAt) {
    }
}
