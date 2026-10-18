package com.supermarket.pos.pos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A cashier's session on one register. The DB guarantees one OPEN shift per register and per cashier. */
@Getter
@Setter
@Entity
@Table(name = "cashier_shifts")
public class CashierShift {

    public enum Status { OPEN, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shift_number", nullable = false, length = 50)
    private String shiftNumber;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "register_id", nullable = false)
    private Long registerId;

    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "opening_float", nullable = false, precision = 15, scale = 2)
    private BigDecimal openingFloat;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "expected_cash", precision = 15, scale = 2)
    private BigDecimal expectedCash;

    @Column(name = "counted_cash", precision = 15, scale = 2)
    private BigDecimal countedCash;

    @Column(precision = 15, scale = 2)
    private BigDecimal variance;

    @Column(name = "variance_approved_by")
    private Long varianceApprovedBy;

    @Column(name = "close_notes", length = 500)
    private String closeNotes;

    @Column(name = "denomination_json", length = 1000)
    private String denominationJson;

    @Version
    private long version;
}
