package com.supermarket.pos.payments.service;

import com.supermarket.pos.payments.dto.PaymentDtos.PaymentRequest;

import java.math.BigDecimal;

/**
 * Integration boundary for one tender type. v1 ships cash, an externally-approved card
 * recorder and loyalty points. A real card gateway would implement this (plus an
 * intent/confirm flow outside the checkout transaction; see docs/02-business-workflows.md W4b).
 */
public interface PaymentProvider {

    /** Tender code this provider handles (CASH, CARD, LOYALTY, ...). */
    String tenderType();

    String providerName();

    /**
     * Validates the tender against the amount still due and returns what it pays.
     * Must not perform side effects: it runs before the sale is written.
     */
    PlannedPayment plan(PaymentRequest request, BigDecimal remaining, PaymentContext context);

    record PaymentContext(Long storeId, Long customerId, long loyaltyBalance, BigDecimal loyaltyPointValue,
                          long minRedeemPoints) {
    }

    record PlannedPayment(String tenderType, String provider, BigDecimal amount, BigDecimal amountTendered,
                          BigDecimal changeGiven, String cardBrand, String cardLast4, String authCode,
                          String reference, Long loyaltyPoints) {
    }
}
