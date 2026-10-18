package com.supermarket.pos.products.mapper;

import com.supermarket.pos.products.dto.ProductDtos.BarcodeDto;
import com.supermarket.pos.products.dto.ProductDtos.CategoryRef;
import com.supermarket.pos.products.dto.ProductDtos.PosProductDto;
import com.supermarket.pos.products.dto.ProductDtos.ProductDto;
import com.supermarket.pos.products.dto.ProductDtos.ProductSummaryDto;
import com.supermarket.pos.products.dto.ProductDtos.PromotionDto;
import com.supermarket.pos.products.dto.ProductDtos.StockDto;
import com.supermarket.pos.products.dto.ProductDtos.TaxRateDto;
import com.supermarket.pos.products.dto.ProductDtos.TaxRateRef;
import com.supermarket.pos.products.entity.Product;
import com.supermarket.pos.products.entity.ProductBarcode;
import com.supermarket.pos.products.entity.ProductPromotion;
import com.supermarket.pos.products.entity.TaxRate;
import com.supermarket.pos.products.service.StockLookup.StockLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Explicit entity → DTO mapping for the catalog. */
public final class ProductMapper {

    private ProductMapper() {
    }

    public static StockDto stock(Product p, Long storeId, StockLevel level) {
        BigDecimal qty = level == null ? BigDecimal.ZERO.setScale(3) : level.quantityOnHand();
        boolean low = p.getReorderLevel().signum() > 0 && qty.compareTo(p.getReorderLevel()) <= 0;
        return new StockDto(storeId, qty, p.getReorderLevel(), low);
    }

    public static ProductSummaryDto toSummary(Product p, boolean showCost, Long storeId, StockLevel level) {
        return new ProductSummaryDto(p.getId(), p.getSku(), p.getName(), p.getBrand(),
                new CategoryRef(p.getCategory().getId(), p.getCategory().getName()), p.getUnitOfMeasure(),
                p.getSellingPrice(), showCost ? p.getCostPrice() : null, taxRef(p.getTaxRate()),
                p.primaryBarcode().map(ProductBarcode::getBarcode).orElse(null), p.isActive(),
                stock(p, storeId, level));
    }

    public static ProductDto toDto(Product p, boolean showCost, boolean pricesIncludeTax, Long storeId,
                                   StockLevel level, List<ProductPromotion> promotions, Instant now) {
        List<BarcodeDto> barcodes = p.getBarcodes().stream()
                .sorted(Comparator.comparing(ProductBarcode::isPrimary).reversed().thenComparing(ProductBarcode::getId))
                .map(b -> new BarcodeDto(b.getId(), b.getBarcode(), b.getBarcodeType(), b.isPrimary()))
                .toList();
        return new ProductDto(p.getId(), p.getSku(), p.getName(), p.getDescription(),
                new CategoryRef(p.getCategory().getId(), p.getCategory().getName()), p.getBrand(),
                p.getUnitOfMeasure(), p.getUnitOfMeasure().isFractional(), showCost ? p.getCostPrice() : null,
                p.getSellingPrice(), taxRef(p.getTaxRate()), pricesIncludeTax, barcodes, stock(p, storeId, level),
                p.getReorderLevel(), p.isTrackBatches(), p.isTrackExpiry(), p.isAllowPriceOverride(),
                p.getImageUrl(), p.isActive(), promotions.stream().map(pr -> toPromotion(pr, now)).toList(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
    }

    public static PosProductDto toPos(Product p, String barcode, ProductPromotion promotion, StockLevel level,
                                      Instant now) {
        return new PosProductDto(p.getId(), p.getSku(),
                barcode != null ? barcode : p.primaryBarcode().map(ProductBarcode::getBarcode).orElse(null),
                p.getName(), p.getBrand(), p.getUnitOfMeasure(), p.getUnitOfMeasure().isFractional(),
                p.getSellingPrice(), p.getTaxRate().getRatePercent(),
                promotion == null ? null : toPromotion(promotion, now),
                level == null ? BigDecimal.ZERO.setScale(3) : level.quantityOnHand(),
                p.isAllowPriceOverride(), p.isActive());
    }

    public static PromotionDto toPromotion(ProductPromotion pr, Instant now) {
        return new PromotionDto(pr.getId(), pr.getName(), pr.getDiscountType(), pr.getDiscountValue(),
                pr.getStartsAt(), pr.getEndsAt(), pr.isActive(), pr.isRunning(now), pr.getVersion());
    }

    public static TaxRateRef taxRef(TaxRate t) {
        return new TaxRateRef(t.getId(), t.getCode(), t.getRatePercent());
    }

    public static TaxRateDto toTaxRate(TaxRate t) {
        return new TaxRateDto(t.getId(), t.getCode(), t.getName(), t.getRatePercent(),
                t.getEffectiveFrom() == null ? null : t.getEffectiveFrom().toString(),
                t.getEffectiveTo() == null ? null : t.getEffectiveTo().toString(), t.isActive());
    }
}
