package com.supermarket.pos.sales.dto;

import com.supermarket.pos.payments.dto.PaymentDtos.PaymentDto;
import com.supermarket.pos.payments.dto.PaymentDtos.PaymentRequest;
import com.supermarket.pos.sales.entity.Sale;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SalesDtos {

    private SalesDtos() {
    }

    public record DiscountRequest(@NotNull @Pattern(regexp = "PERCENT|AMOUNT") String method,
                                  @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal value,
                                  @NotBlank @Size(max = 255) String reason) {
    }

    public record PriceOverrideRequest(@NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal unitPrice,
                                       @NotBlank @Size(max = 255) String reason) {
    }

    public record CartLine(@NotNull Long productId,
                           @NotNull @DecimalMin("0.001") @Digits(integer = 12, fraction = 3) BigDecimal quantity,
                           @Valid DiscountRequest manualDiscount,
                           @Valid PriceOverrideRequest priceOverride) {
    }

    /** The basket as the cashier built it. Prices are never sent: the server prices everything. */
    public record CartRequest(@NotNull Long registerId,
                              Long customerId,
                              @NotEmpty @Size(max = 300) List<@Valid CartLine> items,
                              @Valid DiscountRequest transactionDiscount,
                              @Size(max = 10) List<String> approvalTokens) {
    }

    public record CheckoutRequest(@NotNull Long registerId,
                                  Long customerId,
                                  @NotEmpty @Size(max = 300) List<@Valid CartLine> items,
                                  @Valid DiscountRequest transactionDiscount,
                                  @Size(max = 10) List<String> approvalTokens,
                                  @NotNull @DecimalMin("0.00") BigDecimal expectedGrandTotal,
                                  @NotEmpty @Size(max = 5) List<@Valid PaymentRequest> payments) {

        public CartRequest cart() {
            return new CartRequest(registerId, customerId, items, transactionDiscount, approvalTokens);
        }
    }

    public record LineDto(int lineNumber, Long productId, String name, String sku, String barcode, String unitOfMeasure,
                          BigDecimal quantity, BigDecimal unitPrice, BigDecimal originalUnitPrice, BigDecimal grossAmount,
                          BigDecimal promoDiscount, String promotionName, BigDecimal manualDiscount,
                          BigDecimal allocatedTransactionDiscount, BigDecimal netAmount, BigDecimal taxRatePercent,
                          BigDecimal taxAmount, BigDecimal lineTotal, BigDecimal availableQuantity,
                          boolean stockSufficient, BigDecimal quantityReturned) {
    }

    public record TotalsDto(BigDecimal subtotal, BigDecimal lineDiscountTotal, BigDecimal transactionDiscountTotal,
                            BigDecimal discountTotal, BigDecimal taxTotal, BigDecimal grandTotal, int itemCount) {
    }

    public record RequiredApproval(String action, BigDecimal value, String reason, boolean satisfied) {
    }

    public record Warning(String code, Long productId, String message) {
    }

    public record LoyaltyPreview(Long customerId, String customerName, long balance, BigDecimal balanceValue,
                                 long pointsToEarn) {
    }

    public record QuoteResponse(String currency, boolean pricesIncludeTax, List<LineDto> lines, TotalsDto totals,
                                List<RequiredApproval> requiredApprovals, List<Warning> warnings, LoyaltyPreview loyalty) {
    }

    public record StoreInfo(String name, String addressLine1, String addressLine2, String city, String phone,
                            String taxRegistrationNo, String receiptHeader, String receiptFooter) {
    }

    /** A completed sale, shaped for the receipt. */
    public record SaleDto(Long id, String receiptNumber, Sale.Status status, Sale.Channel channel, Instant completedAt,
                          StoreInfo store, String cashierName, String registerCode, Long customerId, String customerName,
                          String currency, boolean pricesIncludeTax, List<LineDto> lines, TotalsDto totals,
                          BigDecimal amountPaid, BigDecimal amountTendered, BigDecimal changeDue, List<PaymentDto> payments,
                          long loyaltyPointsEarned, long loyaltyPointsRedeemed, BigDecimal refundedTotal,
                          String onlineOrderNumber, boolean reprint, int reprintCount) {
    }

    public record SaleSummaryDto(Long id, String receiptNumber, Sale.Status status, Sale.Channel channel,
                                 Instant completedAt, String cashierName, String registerCode, String customerName,
                                 BigDecimal grandTotal, BigDecimal refundedTotal, int itemCount, List<String> tenders) {
    }
}
