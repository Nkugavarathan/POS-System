package com.supermarket.pos.shop.dto;

import com.supermarket.pos.payments.dto.PaymentDtos.PaymentRequest;
import com.supermarket.pos.shop.entity.OnlineOrder;
import com.supermarket.pos.shop.entity.OnlineOrderItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ShopDtos {

    private ShopDtos() {
    }

    // ----- public catalog ------------------------------------------------------------------------

    public record ShopStoreDto(Long id, String code, String name, String addressLine1, String city, String phone) {
    }

    public record ShopCategoryDto(Long id, String name, long productCount, List<ShopCategoryDto> children) {
    }

    /** stockLevel: IN_STOCK, LOW or OUT (exact quantities are not exposed publicly). */
    public record ShopProductDto(Long id, String name, String brand, String description, Long categoryId,
                                 String categoryName, String unitOfMeasure, boolean fractional, BigDecimal price,
                                 BigDecimal offerPrice, String offerLabel, boolean rescue, Long rescueDaysLeft,
                                 String stockLevel, String imageUrl) {
    }

    public record RescueDealDto(Long productId, String name, String brand, String unitOfMeasure, BigDecimal price,
                                BigDecimal offerPrice, BigDecimal percentOff, LocalDate expiryDate, long daysLeft,
                                BigDecimal quantityLeft, Instant endsAt, String imageUrl) {
    }

    public record CartItem(@NotNull Long productId,
                           @NotNull @DecimalMin("0.001") @Digits(integer = 12, fraction = 3) BigDecimal quantity,
                           OnlineOrderItem.Preference substitutionPreference) {
    }

    public record CartQuoteRequest(@NotNull Long storeId, @NotEmpty @Size(max = 60) List<@Valid CartItem> items) {
    }

    public record CartLineDto(Long productId, String name, String unitOfMeasure, BigDecimal quantity, BigDecimal unitPrice,
                              BigDecimal promoDiscount, String promotionName, BigDecimal lineTotal, boolean available) {
    }

    public record CartQuoteDto(String currency, boolean pricesIncludeTax, List<CartLineDto> lines, BigDecimal subtotal,
                               BigDecimal discountTotal, BigDecimal taxTotal, BigDecimal grandTotal,
                               List<Long> unavailableProductIds, BigDecimal savings) {
    }

    // ----- account ---------------------------------------------------------------------------------

    public record RegisterRequest(@NotBlank @Size(max = 120) String fullName,
                                  @NotBlank @Email @Size(max = 120) String email,
                                  @NotBlank @Size(max = 20) String phone,
                                  @NotBlank @Size(min = 8, max = 128)
                                  @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain a letter and a digit")
                                  String password,
                                  boolean marketingConsent,
                                  Long preferredStoreId,
                                  /* existing in-store loyalty card for this phone, to join the two histories */
                                  @Size(max = 20) String loyaltyCardNumber) {
    }

    public record ShopLoginRequest(@NotBlank @Size(max = 120) String email, @NotBlank @Size(max = 128) String password) {
    }

    public record ShopMeDto(Long accountId, Long customerId, String fullName, String email, String maskedPhone,
                            String loyaltyCard, long points, BigDecimal pointsValue, Long preferredStoreId,
                            long unreadNotifications) {
    }

    /** Proves possession of an in-store loyalty card: both card number and phone must match. */
    public record LinkCardRequest(@NotBlank @Size(max = 20) String cardNumber, @NotBlank @Size(max = 20) String phone) {
    }

    public record PreferredStoreRequest(@NotNull Long storeId) {
    }

    public record NotificationDto(Long id, String kind, String title, String body, Long orderId, String orderNumber, boolean read,
                                  Instant createdAt) {
    }

    /** "Running low?" suggestion from the customer's own buying rhythm (store and online). */
    public record BuyAgainDto(Long productId, String name, String unitOfMeasure, BigDecimal price, BigDecimal offerPrice,
                              long purchases, Instant lastBoughtAt, Long averageIntervalDays, Long dueInDays,
                              boolean dueSoon, String stockLevel) {
    }

    // ----- orders ------------------------------------------------------------------------------------

    public record PlaceOrderRequest(@NotNull Long storeId,
                                    Instant pickupAfter,
                                    @Size(max = 500) String note,
                                    @NotEmpty @Size(max = 60) List<@Valid CartItem> items,
                                    @NotNull @DecimalMin("0.00") BigDecimal expectedTotal) {
    }

    public record SubstituteDto(Long productId, String name, BigDecimal quantity, BigDecimal lineTotal) {
    }

    public record OrderLineDto(Long id, int lineNumber, Long productId, String name, String sku, String barcode,
                               String unitOfMeasure, BigDecimal quantity, BigDecimal unitPrice, BigDecimal promoDiscount,
                               String promotionName, BigDecimal lineTotal, OnlineOrderItem.Preference preference,
                               OnlineOrderItem.PickStatus pickStatus, BigDecimal pickedQuantity, SubstituteDto substitute,
                               BigDecimal finalLineTotal) {
    }

    public record OrderEventDto(OnlineOrder.Status status, String message, String actorType, Instant at) {
    }

    public record ProgressDto(int resolved, int total, int pendingSubstitutions) {
    }

    public record OrderDto(Long id, String orderNumber, OnlineOrder.Status status, ShopStoreDto store, String pickupCode,
                           Instant pickupAfter, Instant placedAt, Instant readyAt, Instant collectBy, Instant arrivedAt,
                           Instant collectedAt, Instant cancelledAt, String cancelReason, String currency,
                           boolean pricesIncludeTax, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal taxTotal,
                           BigDecimal grandTotal, BigDecimal finalTotal, BigDecimal payableTotal, ProgressDto progress,
                           String customerNote, String customerName, String customerPhone, Long saleId,
                           List<OrderLineDto> lines, List<OrderEventDto> events, boolean canCancel, boolean canCheckIn,
                           long version) {
    }

    public record OrderSummaryDto(Long id, String orderNumber, OnlineOrder.Status status, Instant placedAt,
                                  Instant pickupAfter, Instant arrivedAt, int lines, BigDecimal payableTotal,
                                  String storeName, String customerName, ProgressDto progress) {
    }

    public record SubstitutionDecision(@NotNull Boolean accept) {
    }

    // ----- staff -----------------------------------------------------------------------------------------

    public record PickRequest(@DecimalMin("0.001") @Digits(integer = 12, fraction = 3) BigDecimal pickedQuantity) {
    }

    public record SubstituteRequest(@NotNull Long productId,
                                    @NotNull @DecimalMin("0.001") @Digits(integer = 12, fraction = 3) BigDecimal quantity) {
    }

    public record CancelRequest(@NotBlank @Size(max = 255) String reason) {
    }

    public record CollectRequest(@NotNull Long registerId,
                                 @NotBlank @Pattern(regexp = "^\\d{6}$", message = "6-digit pickup code") String pickupCode,
                                 @NotEmpty @Size(max = 5) List<@Valid PaymentRequest> payments,
                                 @NotNull BigDecimal expectedTotal) {
    }

    public record BoardSummaryDto(Map<String, Long> counts, long arrivedWaiting) {
    }

    public record RescueRunDto(int created, int ended) {
    }
}
