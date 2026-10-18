package com.supermarket.pos.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Immutable ledger entry. {@code quantityAfter = quantityBefore + quantityChange} (DB-checked). */
@Getter
@Setter
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "batch_id", updatable = false)
    private Long batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30, updatable = false)
    private MovementType movementType;

    @Column(name = "quantity_change", nullable = false, precision = 15, scale = 3, updatable = false)
    private BigDecimal quantityChange;

    @Column(name = "quantity_before", nullable = false, precision = 15, scale = 3, updatable = false)
    private BigDecimal quantityBefore;

    @Column(name = "quantity_after", nullable = false, precision = 15, scale = 3, updatable = false)
    private BigDecimal quantityAfter;

    @Column(name = "unit_cost", precision = 15, scale = 4, updatable = false)
    private BigDecimal unitCost;

    @Column(name = "reference_type", nullable = false, length = 30, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", updatable = false)
    private Long referenceId;

    @Column(name = "reference_line_id", updatable = false)
    private Long referenceLineId;

    @Column(length = 255, updatable = false)
    private String reason;

    @Column(name = "performed_by", nullable = false, updatable = false)
    private Long performedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
