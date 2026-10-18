package com.supermarket.pos.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Current stock of one product in one store. Only {@code InventoryService} changes it, always
 * under a row lock and always together with a {@link StockMovement}.
 */
@Getter
@Setter
@Entity
@Table(name = "inventory_balances")
public class InventoryBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity_on_hand", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantityOnHand;

    /** Moving average unit cost (estimates, valuation and margin). */
    @Column(name = "average_cost", nullable = false, precision = 15, scale = 4)
    private BigDecimal averageCost;

    @Column(name = "last_movement_at")
    private Instant lastMovementAt;

    @Version
    private long version;
}
