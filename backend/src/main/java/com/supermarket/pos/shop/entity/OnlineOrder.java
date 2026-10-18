package com.supermarket.pos.shop.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Click-and-collect order. Prices are locked when placed; packing can only lower the bill
 * (unavailable items drop out, substitutes never cost more than the original line). The customer
 * pays {@code finalTotal} at the counter when collecting.
 */
@Getter
@Setter
@Entity
@Table(name = "online_orders")
public class OnlineOrder {

    public enum Status { PLACED, CONFIRMED, PACKING, READY_FOR_PICKUP, COLLECTED, CANCELLED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, length = 30, updatable = false)
    private String orderNumber;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "customer_account_id", nullable = false, updatable = false)
    private Long customerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PLACED;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "pickup_code", nullable = false, length = 6, updatable = false)
    private String pickupCode;

    @Column(name = "pickup_after", updatable = false)
    private Instant pickupAfter;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "prices_include_tax", nullable = false, updatable = false)
    private boolean pricesIncludeTax;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "discount_total", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal discountTotal;

    @Column(name = "tax_total", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal taxTotal;

    @Column(name = "grand_total", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal grandTotal;

    @Column(name = "final_total", precision = 15, scale = 2)
    private BigDecimal finalTotal;

    @Column(name = "final_tax_total", precision = 15, scale = 2)
    private BigDecimal finalTaxTotal;

    @Column(name = "item_count", nullable = false, updatable = false)
    private int itemCount;

    @Column(name = "customer_note", length = 500, updatable = false)
    private String customerNote;

    @Column(name = "stock_picked", nullable = false)
    private boolean stockPicked;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "packing_started_at")
    private Instant packingStartedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "collect_by")
    private Instant collectBy;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "collected_at")
    private Instant collectedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "sale_id")
    private Long saleId;

    @Column(name = "handled_by")
    private Long handledBy;

    @Version
    private long version;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @OrderBy("lineNumber")
    private List<OnlineOrderItem> items = new ArrayList<>();

    /** Total the customer will pay: the packed total once packing is done, otherwise the ordered total. */
    public BigDecimal payableTotal() {
        return finalTotal != null ? finalTotal : grandTotal;
    }
}
