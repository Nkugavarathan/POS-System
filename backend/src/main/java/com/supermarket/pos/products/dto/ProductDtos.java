package com.supermarket.pos.products.dto;

import com.supermarket.pos.products.entity.BarcodeType;
import com.supermarket.pos.products.entity.ProductPromotion.DiscountType;
import com.supermarket.pos.products.entity.UnitOfMeasure;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record CategoryRef(Long id, String name) {
    }

    public record TaxRateRef(Long id, String code, BigDecimal ratePercent) {
    }

    public record BarcodeDto(Long id, String barcode, BarcodeType type, boolean primary) {
    }

    public record StockDto(Long storeId, BigDecimal quantityOnHand, BigDecimal reorderLevel, boolean lowStock) {
    }

    public record PromotionDto(Long id, String name, DiscountType type, BigDecimal value, Instant startsAt,
                               Instant endsAt, boolean active, boolean running, long version) {
    }

    /** List row. {@code costPrice} is null unless the caller holds PRODUCT_VIEW_COST. */
    public record ProductSummaryDto(Long id, String sku, String name, String brand, CategoryRef category,
                                    UnitOfMeasure unitOfMeasure, BigDecimal sellingPrice, BigDecimal costPrice,
                                    TaxRateRef taxRate, String primaryBarcode, boolean active, StockDto stock) {
    }

    public record ProductDto(Long id, String sku, String name, String description, CategoryRef category, String brand,
                             UnitOfMeasure unitOfMeasure, boolean fractionalQuantity, BigDecimal costPrice,
                             BigDecimal sellingPrice, TaxRateRef taxRate, boolean pricesIncludeTax,
                             List<BarcodeDto> barcodes, StockDto stock, BigDecimal reorderLevel, boolean trackBatches,
                             boolean trackExpiry, boolean allowPriceOverride, String imageUrl, boolean active,
                             List<PromotionDto> promotions, Instant createdAt, Instant updatedAt, long version) {
    }

    /** What the cashier screen needs after a scan or search. Prices are display values; checkout re-prices. */
    public record PosProductDto(Long id, String sku, String barcode, String name, String brand,
                                UnitOfMeasure unitOfMeasure, boolean fractionalQuantity, BigDecimal sellingPrice,
                                BigDecimal taxRatePercent, PromotionDto activePromotion, BigDecimal quantityOnHand,
                                boolean allowPriceOverride, boolean active) {
    }

    public record ProductRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{1,39}$",
                    message = "2-40 upper-case letters, digits or dashes") String sku,
            @NotBlank @Size(min = 2, max = 150) String name,
            @Size(max = 1000) String description,
            @NotNull Long categoryId,
            @Size(max = 80) String brand,
            @NotNull UnitOfMeasure unitOfMeasure,
            @NotNull @DecimalMin("0.0000") @Digits(integer = 11, fraction = 4) BigDecimal costPrice,
            @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal sellingPrice,
            @NotNull Long taxRateId,
            @NotNull @DecimalMin("0.000") @Digits(integer = 12, fraction = 3) BigDecimal reorderLevel,
            boolean trackBatches,
            boolean trackExpiry,
            boolean allowPriceOverride,
            @Size(max = 500) @Pattern(regexp = "^https://\\S+$", message = "must be an https:// URL") String imageUrl,
            /** Only used on create; manage barcodes later through the barcode endpoints. */
            @Size(max = 10) List<@NotBlank @Size(max = 32) String> barcodes,
            Long version) {
    }

    public record ProductStatusRequest(@NotNull Boolean active, @NotNull Long version) {
    }

    public record BarcodeRequest(@NotBlank @Size(max = 32) String barcode, String format, boolean primary) {
    }

    public record PromotionRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull DiscountType type,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2) BigDecimal value,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            boolean active,
            Long version) {
    }

    public record TaxRateDto(Long id, String code, String name, BigDecimal ratePercent, String effectiveFrom,
                             String effectiveTo, boolean active) {
    }

    public record TaxRateRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9_]{2,20}$") String code,
            @NotBlank @Size(max = 80) String name,
            @NotNull @DecimalMin("0.000") @DecimalMax("100.000") @Digits(integer = 3, fraction = 3) BigDecimal ratePercent,
            boolean active) {
    }
}
