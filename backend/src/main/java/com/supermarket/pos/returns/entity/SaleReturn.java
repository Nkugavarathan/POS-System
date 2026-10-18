package com.supermarket.pos.returns.entity;

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

/** A return against one original sale (table {@code returns}; "return" is a Java keyword). */
@Getter
@Setter
@Entity
@Table(name = "returns")
public class SaleReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_number", nullable = false, length = 40, updatable = false)
    private String returnNumber;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Column(name = "register_id", nullable = false, updatable = false)
    private Long registerId;

    @Column(name = "shift_id", nullable = false, updatable = false)
    private Long shiftId;

    @Column(name = "original_sale_id", nullable = false, updatable = false)
    private Long originalSaleId;

    @Column(name = "processed_by", nullable = false, updatable = false)
    private Long processedBy;

    @Column(name = "approved_by", updatable = false)
    private Long approvedBy;

    @Column(name = "reason_code", nullable = false, length = 40, updatable = false)
    private String reasonCode;

    @Column(length = 500, updatable = false)
    private String notes;

    @Column(name = "refund_total", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal refundTotal;

    @Column(name = "tax_total", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal taxTotal;

    @Column(name = "loyalty_points_reversed", nullable = false)
    private long loyaltyPointsReversed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "saleReturn", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<ReturnItem> items = new ArrayList<>();
}
