package com.supermarket.pos.returns.entity;

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
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "return_items")
public class ReturnItem {

    public enum Condition { SALEABLE, DAMAGED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id", nullable = false, updatable = false)
    private SaleReturn saleReturn;

    @Column(name = "sale_item_id", nullable = false, updatable = false)
    private Long saleItemId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(nullable = false, precision = 15, scale = 3, updatable = false)
    private BigDecimal quantity;

    @Column(name = "refund_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal refundAmount;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal taxAmount;

    @Column(nullable = false, updatable = false)
    private boolean restock;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false, length = 20, updatable = false)
    private Condition itemCondition;

    @Column(length = 255, updatable = false)
    private String reason;
}
