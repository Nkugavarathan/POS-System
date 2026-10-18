package com.supermarket.pos.sales.entity;

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

/** A completed, fully paid sale. Immutable except status, returned quantities and reprint count. */
@Getter
@Setter
@Entity
@Table(name = "sales")
public class Sale {

    public enum Status { COMPLETED, PARTIALLY_RETURNED, FULLY_RETURNED }

    public enum Channel { POS, ONLINE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_number", nullable = false, length = 50, updatable = false)
    private String receiptNumber;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Column(name = "register_id", nullable = false, updatable = false)
    private Long registerId;

    @Column(name = "shift_id", nullable = false, updatable = false)
    private Long shiftId;

    @Column(name = "cashier_id", nullable = false, updatable = false)
    private Long cashierId;

    @Column(name = "customer_id", updatable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Channel channel = Channel.POS;

    @Column(name = "online_order_id", updatable = false)
    private Long onlineOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.COMPLETED;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "prices_include_tax", nullable = false, updatable = false)
    private boolean pricesIncludeTax;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "line_discount_total", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal lineDiscountTotal;

    @Column(name = "transaction_discount_total", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal transactionDiscountTotal;

    @Column(name = "discount_total", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal discountTotal;

    @Column(name = "tax_total", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal taxTotal;

    @Column(name = "grand_total", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal grandTotal;

    @Column(name = "amount_paid", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amountPaid;

    @Column(name = "amount_tendered", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amountTendered;

    @Column(name = "change_due", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal changeDue;

    @Column(name = "item_count", nullable = false, updatable = false)
    private int itemCount;

    @Column(name = "cost_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal costTotal;

    @Column(name = "refunded_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal refundedTotal = BigDecimal.ZERO;

    @Column(name = "loyalty_points_earned", nullable = false)
    private long loyaltyPointsEarned;

    @Column(name = "loyalty_points_redeemed", nullable = false, updatable = false)
    private long loyaltyPointsRedeemed;

    @Column(name = "reprint_count", nullable = false)
    private int reprintCount;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "sale", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @OrderBy("lineNumber")
    private List<SaleItem> items = new ArrayList<>();
}
