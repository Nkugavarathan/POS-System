package com.supermarket.pos.purchases.entity;

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
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "goods_receipt_items")
public class GoodsReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false, updatable = false)
    private GoodsReceipt goodsReceipt;

    @Column(name = "purchase_order_item_id", updatable = false)
    private Long purchaseOrderItemId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "quantity_received", nullable = false, precision = 15, scale = 3, updatable = false)
    private BigDecimal quantityReceived;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 4, updatable = false)
    private BigDecimal unitCost;

    @Column(name = "batch_number", length = 50, updatable = false)
    private String batchNumber;

    @Column(name = "expiry_date", updatable = false)
    private LocalDate expiryDate;

    @Column(name = "batch_id")
    private Long batchId;
}
