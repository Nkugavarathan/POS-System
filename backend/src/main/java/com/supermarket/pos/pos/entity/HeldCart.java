package com.supermarket.pos.pos.entity;

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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A parked basket. Only products and quantities are kept; prices are re-quoted on resume. */
@Getter
@Setter
@Entity
@Table(name = "held_carts")
public class HeldCart {

    public enum Status { HELD, RESUMED, CANCELLED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "register_id", nullable = false)
    private Long registerId;

    @Column(name = "shift_id", nullable = false)
    private Long shiftId;

    @Column(name = "held_by", nullable = false)
    private Long heldBy;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(length = 60)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.HELD;

    @Column(name = "held_at", nullable = false)
    private Instant heldAt;

    @Column(name = "resumed_at")
    private Instant resumedAt;

    @Column(name = "resumed_by")
    private Long resumedBy;

    @Version
    private long version;

    @OneToMany(mappedBy = "heldCart", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber")
    private List<HeldCartItem> items = new ArrayList<>();
}
