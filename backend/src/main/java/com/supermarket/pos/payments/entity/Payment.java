package com.supermarket.pos.payments.entity;

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

/**
 * One tender applied to a sale. {@code amount} is what it pays toward the total; for cash
 * {@code amountTendered - amount = changeGiven}. Card data is limited to brand, last 4 digits
 * and the terminal's approval code: a full card number is never accepted or stored.
 */
@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_id", nullable = false, updatable = false)
    private Long saleId;

    @Column(name = "tender_type", nullable = false, length = 20, updatable = false)
    private String tenderType;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "amount_tendered", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amountTendered;

    @Column(name = "change_given", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal changeGiven;

    @Column(nullable = false, length = 20)
    private String status = "CAPTURED";

    @Column(nullable = false, length = 40, updatable = false)
    private String provider;

    @Column(name = "provider_reference", length = 80, updatable = false)
    private String providerReference;

    @Column(name = "card_brand", length = 20, updatable = false)
    private String cardBrand;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_last4", length = 4, updatable = false)
    private String cardLast4;

    @Column(name = "auth_code", length = 20, updatable = false)
    private String authCode;

    @Column(name = "loyalty_points", updatable = false)
    private Long loyaltyPoints;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private Long recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
