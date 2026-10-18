package com.supermarket.pos.products.entity;

import com.supermarket.pos.common.persistence.AuditableEntity;
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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A time-boxed product discount: PERCENT off the unit price, or a fixed AMOUNT off each unit.
 * When several promotions overlap, the pricing engine applies the one worth most to the customer.
 */
@Getter
@Setter
@Entity
@Table(name = "product_promotions")
public class ProductPromotion extends AuditableEntity {

    public enum DiscountType { PERCENT, AMOUNT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 10)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by")
    private Long createdBy;

    /** null = every store; otherwise only this store (rescue deals are always store-specific). */
    @Column(name = "store_id")
    private Long storeId;

    /** MANUAL (created by staff) or RESCUE (generated for a batch close to expiry). */
    @Column(nullable = false, length = 10)
    private String source = "MANUAL";

    @Column(name = "batch_id")
    private Long batchId;

    @Version
    private long version;

    public boolean isRunning(Instant now) {
        return active && !now.isBefore(startsAt) && now.isBefore(endsAt);
    }
}
