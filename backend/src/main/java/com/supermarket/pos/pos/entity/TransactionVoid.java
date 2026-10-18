package com.supermarket.pos.pos.entity;

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

/**
 * A line or whole basket removed before payment. No sale exists, so this is the only trace;
 * it feeds the loss-prevention (voids) report. Not a refund.
 */
@Getter
@Setter
@Entity
@Table(name = "transaction_voids")
public class TransactionVoid {

    public enum Type { LINE, CART }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "register_id", nullable = false)
    private Long registerId;

    @Column(name = "shift_id", nullable = false)
    private Long shiftId;

    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "void_type", nullable = false, length = 10)
    private Type voidType;

    @Column(name = "reason_code", nullable = false, length = 30)
    private String reasonCode;

    @Column(name = "reason_text", length = 255)
    private String reasonText;

    @Column(name = "items_json", nullable = false, columnDefinition = "longtext")
    private String itemsJson;

    @Column(name = "estimated_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal estimatedAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
