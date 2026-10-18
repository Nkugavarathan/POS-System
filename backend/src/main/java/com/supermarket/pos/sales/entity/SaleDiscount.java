package com.supermarket.pos.sales.entity;

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

/** Every discount given on a sale, with who applied and who approved it (discount report). */
@Getter
@Setter
@Entity
@Table(name = "sale_discounts")
public class SaleDiscount {

    public enum Source { PROMOTION, MANUAL_LINE, MANUAL_TRANSACTION, PRICE_OVERRIDE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_id", nullable = false)
    private Long saleId;

    @Column(name = "sale_item_id")
    private Long saleItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Source source;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal value;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String reason;

    @Column(name = "promotion_id")
    private Long promotionId;

    @Column(name = "applied_by", nullable = false)
    private Long appliedBy;

    @Column(name = "approved_by")
    private Long approvedBy;
}
