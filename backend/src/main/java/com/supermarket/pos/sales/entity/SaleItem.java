package com.supermarket.pos.sales.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A sale line with every pricing step stored (snapshots), so receipts, returns and reports
 * never depend on today's product data.
 */
@Getter
@Setter
@Entity
@Table(name = "sale_items")
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, updatable = false)
    private Sale sale;

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

    @Column(name = "original_unit_price", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal originalUnitPrice;

    @Column(name = "price_override_by", updatable = false)
    private Long priceOverrideBy;

    @Column(name = "gross_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal grossAmount;

    @Column(name = "promo_discount_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal promoDiscountAmount;

    @Column(name = "manual_discount_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal manualDiscountAmount;

    @Column(name = "allocated_txn_discount_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal allocatedTxnDiscountAmount;

    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal netAmount;

    @Column(name = "tax_rate_percent", nullable = false, precision = 6, scale = 3, updatable = false)
    private BigDecimal taxRatePercent;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal taxAmount;

    /** What the customer pays for the line (net, plus tax when prices exclude tax). */
    @Column(name = "line_total", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal lineTotal;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "cost_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal costAmount;

    @Column(name = "quantity_returned", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantityReturned = BigDecimal.ZERO;

    @Column(name = "refunded_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;
}
