package com.supermarket.pos.purchases.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A delivery that actually arrived (GRN). Only received goods change stock. */
@Getter
@Setter
@Entity
@Table(name = "goods_receipts")
public class GoodsReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grn_number", nullable = false, length = 40, updatable = false)
    private String grnNumber;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private Long supplierId;

    @Column(name = "purchase_order_id", updatable = false)
    private Long purchaseOrderId;

    @Column(name = "supplier_invoice_no", length = 60, updatable = false)
    private String supplierInvoiceNo;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "received_by", nullable = false, updatable = false)
    private Long receivedBy;

    @Column(name = "total_cost", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal totalCost;

    @Column(length = 500, updatable = false)
    private String notes;

    @OneToMany(mappedBy = "goodsReceipt", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<GoodsReceiptItem> items = new ArrayList<>();
}
