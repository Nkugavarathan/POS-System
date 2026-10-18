package com.supermarket.pos.inventory.entity;

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

@Getter
@Setter
@Entity
@Table(name = "stock_count_items")
public class StockCountItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_count_id", nullable = false)
    private StockCount stockCount;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** System quantity when the count was created (for reporting drift during the count). */
    @Column(name = "system_quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal systemQuantity;

    @Column(name = "counted_quantity", precision = 15, scale = 3)
    private BigDecimal countedQuantity;

    /** Live system quantity at posting; variance = counted - quantityAtPost. */
    @Column(name = "quantity_at_post", precision = 15, scale = 3)
    private BigDecimal quantityAtPost;

    @Column(precision = 15, scale = 3)
    private BigDecimal variance;
}
