package com.supermarket.pos.payments.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /**
     * Card details from an EXTERNAL terminal that already approved the payment. Only the last
     * four digits are accepted, never a full card number.
     */
    public record CardDetails(@Size(max = 20) String brand,
                              @Pattern(regexp = "^\\d{4}$", message = "last 4 digits only") String last4,
                              @NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9-]{2,20}$") String authCode,
                              @Size(max = 80) String terminalReference) {
    }

    /**
     * {@code amount} is required for non-cash tenders. For CASH send {@code amountTendered}
     * (the notes handed over); cash always covers whatever remains after the other tenders.
     */
    public record PaymentRequest(@NotBlank @Size(max = 20) String tenderType,
                                 @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
                                 @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal amountTendered,
                                 @Valid CardDetails card) {
    }

    public record PaymentDto(String tenderType, BigDecimal amount, BigDecimal amountTendered, BigDecimal changeGiven,
                             String cardBrand, String cardLast4, String authCode, Long loyaltyPoints) {
    }
}
