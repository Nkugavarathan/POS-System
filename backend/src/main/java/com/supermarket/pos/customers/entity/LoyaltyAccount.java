package com.supermarket.pos.customers.entity;

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

import java.time.Instant;

/** {@code pointsBalance} is a cached sum of the append-only loyalty ledger. */
@Getter
@Setter
@Entity
@Table(name = "loyalty_accounts")
public class LoyaltyAccount {

    public enum Status { ACTIVE, BLOCKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "card_number", nullable = false, length = 20)
    private String cardNumber;

    @Column(name = "points_balance", nullable = false)
    private long pointsBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Version
    private long version;
}
