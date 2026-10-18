package com.supermarket.pos.payments.service;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.payments.dto.PaymentDtos.PaymentRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** The v1 tender implementations. */
@Configuration
public class TenderProviders {

    /** Cash: the customer hands over at least what remains; the difference is change. */
    @Bean
    public PaymentProvider cashProvider() {
        return new PaymentProvider() {
            public String tenderType() {
                return "CASH";
            }

            public String providerName() {
                return "CASH_DRAWER";
            }

            public PlannedPayment plan(PaymentRequest r, BigDecimal remaining, PaymentContext ctx) {
                BigDecimal tendered = MoneyPolicy.amount(r.amountTendered() != null ? r.amountTendered() : r.amount());
                if (tendered.compareTo(remaining) < 0) {
                    throw new ApiException(ErrorCode.INSUFFICIENT_TENDER, "Cash tendered (" + tendered
                            + ") is less than the amount due (" + remaining + ").",
                            Map.of("due", remaining.toPlainString(), "tendered", tendered.toPlainString()));
                }
                return new PlannedPayment("CASH", providerName(), remaining, tendered, tendered.subtract(remaining),
                        null, null, null, null, null);
            }
        };
    }

    /**
     * Card approved on an external terminal. The system records it; it does not charge cards.
     * The approval code is mandatory so the record can be reconciled with the terminal report.
     */
    @Bean
    public PaymentProvider manualCardProvider() {
        return new PaymentProvider() {
            public String tenderType() {
                return "CARD";
            }

            public String providerName() {
                return "EXTERNAL_TERMINAL";
            }

            public PlannedPayment plan(PaymentRequest r, BigDecimal remaining, PaymentContext ctx) {
                if (r.amount() == null) {
                    throw new ApiException(ErrorCode.PAYMENT_INVALID, "Enter the card amount.");
                }
                BigDecimal amount = MoneyPolicy.amount(r.amount());
                if (amount.compareTo(remaining) > 0) {
                    throw new ApiException(ErrorCode.PAYMENT_INVALID, "The card amount is more than the amount due ("
                            + remaining + "). Card payments give no change.");
                }
                if (r.card() == null || r.card().authCode() == null || r.card().authCode().isBlank()) {
                    throw new ApiException(ErrorCode.PAYMENT_INVALID,
                            "Enter the approval code printed by the card terminal.");
                }
                return new PlannedPayment("CARD", providerName(), amount, amount, MoneyPolicy.ZERO,
                        r.card().brand() == null ? null : r.card().brand().trim().toUpperCase(),
                        r.card().last4(), r.card().authCode().trim().toUpperCase(),
                        r.card().terminalReference(), null);
            }
        };
    }

    /** Loyalty points as tender: points × point value, rounded so it never pays more than due. */
    @Bean
    public PaymentProvider loyaltyProvider() {
        return new PaymentProvider() {
            public String tenderType() {
                return "LOYALTY";
            }

            public String providerName() {
                return "LOYALTY_LEDGER";
            }

            public PlannedPayment plan(PaymentRequest r, BigDecimal remaining, PaymentContext ctx) {
                if (ctx.customerId() == null) {
                    throw new ApiException(ErrorCode.PAYMENT_INVALID, "Select the customer to pay with loyalty points.");
                }
                if (r.amount() == null || ctx.loyaltyPointValue().signum() <= 0) {
                    throw new ApiException(ErrorCode.PAYMENT_INVALID, "Enter the amount to pay with points.");
                }
                BigDecimal amount = MoneyPolicy.amount(r.amount()).min(remaining);
                long points = amount.divide(ctx.loyaltyPointValue(), 0, RoundingMode.CEILING).longValueExact();
                if (points < ctx.minRedeemPoints()) {
                    throw new ApiException(ErrorCode.PAYMENT_INVALID, "At least " + ctx.minRedeemPoints()
                            + " points must be redeemed at a time.");
                }
                if (points > ctx.loyaltyBalance()) {
                    throw new ApiException(ErrorCode.PAYMENT_INVALID, "The customer has only " + ctx.loyaltyBalance()
                            + " points.", Map.of("balance", ctx.loyaltyBalance()));
                }
                return new PlannedPayment("LOYALTY", providerName(), amount, amount, MoneyPolicy.ZERO,
                        null, null, null, null, points);
            }
        };
    }
}
