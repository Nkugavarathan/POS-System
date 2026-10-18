package com.supermarket.pos.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * An ordered line plus its packing outcome. PICKED: packed as ordered (weighed items may differ
 * slightly in quantity). SUBSTITUTE_PROPOSED: waiting for the customer. SUBSTITUTED: customer
 * accepted the replacement. UNAVAILABLE: dropped from the order (not charged).
 */
@Getter
@Setter
@Entity
@Table(name = "online_order_items")
public class OnlineOrderItem {

    public enum PickStatus { PENDING, PICKED, SUBSTITUTE_PROPOSED, SUBSTITUTED, UNAVAILABLE }

    public enum Preference { SUBSTITUTE, SKIP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "online_order_id", nullable = false, updatable = false)
    private OnlineOrder order;

    @Column(name = "line_number", nullable = false, updatable = false)
    private int lineNumber;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "sku_snapshot", nullable = false, length = 40, updatable = false)
    private String skuSnapshot;

    @Column(name = "barcode_snapshot", length = 32, updatable = false)
    private String barcodeSnapshot;

    @Column(name = "product_name_snapshot", nullable = false, length = 150, updatable = false)
    private String productNameSnapshot;

    @Column(name = "category_id", nullable = false, updatable = false)
    private Long categoryId;

    @Column(name = "unit_of_measure", nullable = false, length = 10, updatable = false)
    private String unitOfMeasure;

    @Column(nullable = false, precision = 15, scale = 3, updatable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    @Column(name = "gross_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal grossAmount;

    @Column(name = "promo_discount_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal promoDiscountAmount;

    @Column(name = "promotion_name", length = 100, updatable = false)
    private String promotionName;

    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal netAmount;

    @Column(name = "tax_rate_percent", nullable = false, precision = 6, scale = 3, updatable = false)
    private BigDecimal taxRatePercent;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal taxAmount;

    @Column(name = "line_total", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal lineTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "substitution_preference", nullable = false, length = 10, updatable = false)
    private Preference substitutionPreference = Preference.SUBSTITUTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "pick_status", nullable = false, length = 20)
    private PickStatus pickStatus = PickStatus.PENDING;

    @Column(name = "picked_quantity", precision = 15, scale = 3)
    private BigDecimal pickedQuantity;

    @Column(name = "substitute_product_id")
    private Long substituteProductId;

    @Column(name = "substitute_name", length = 150)
    private String substituteName;

    @Column(name = "substitute_quantity", precision = 15, scale = 3)
    private BigDecimal substituteQuantity;

    @Column(name = "substitute_line_total", precision = 15, scale = 2)
    private BigDecimal substituteLineTotal;

    @Column(name = "substitute_tax_rate", precision = 6, scale = 3)
    private BigDecimal substituteTaxRate;

    @Column(name = "final_product_id")
    private Long finalProductId;

    @Column(name = "final_quantity", precision = 15, scale = 3)
    private BigDecimal finalQuantity;

    @Column(name = "final_line_total", precision = 15, scale = 2)
    private BigDecimal finalLineTotal;

    @Column(name = "final_tax_amount", precision = 15, scale = 2)
    private BigDecimal finalTaxAmount;

    @Column(name = "unit_cost", precision = 15, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "cost_amount", precision = 15, scale = 2)
    private BigDecimal costAmount;

    public boolean isResolved() {
        return pickStatus == PickStatus.PICKED || pickStatus == PickStatus.SUBSTITUTED || pickStatus == PickStatus.UNAVAILABLE;
    }
}
