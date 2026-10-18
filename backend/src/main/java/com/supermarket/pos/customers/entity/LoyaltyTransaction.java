package com.supermarket.pos.customers.entity;

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

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "loyalty_transactions")
public class LoyaltyTransaction {

    public enum Type { EARN, REDEEM, EARN_REVERSAL, REDEEM_REVERSAL, ADJUSTMENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loyalty_account_id", nullable = false, updatable = false)
    private Long loyaltyAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20, updatable = false)
    private Type transactionType;

    @Column(nullable = false, updatable = false)
    private long points;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private long balanceAfter;

    @Column(name = "reference_type", length = 30, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", updatable = false)
    private Long referenceId;

    @Column(length = 255, updatable = false)
    private String reason;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
