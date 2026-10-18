package com.supermarket.pos.returns.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/** Money given back. Card refunds are performed on the external terminal and recorded here. */
@Getter
@Setter
@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_id", nullable = false, updatable = false)
    private Long returnId;

    @Column(name = "tender_type", nullable = false, length = 20, updatable = false)
    private String tenderType;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(length = 80, updatable = false)
    private String reference;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_last4", length = 4, updatable = false)
    private String cardLast4;

    @Column(name = "loyalty_points", updatable = false)
    private Long loyaltyPoints;

    @Column(name = "processed_by", nullable = false, updatable = false)
    private Long processedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
