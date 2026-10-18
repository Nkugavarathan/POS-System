package com.supermarket.pos.auth.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/** A single-use, short-lived manager approval bound to one action, store and requesting user. */
@Getter
@Setter
@Entity
@Table(name = "approval_grants")
public class ApprovalGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ApprovalAction action;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "approved_by", nullable = false)
    private Long approvedBy;

    @Column(name = "register_id")
    private Long registerId;

    @Column(name = "limit_value", precision = 15, scale = 2)
    private BigDecimal limitValue;

    @Column(name = "context_json", length = 1000)
    private String contextJson;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "consumed_ref_type", length = 40)
    private String consumedRefType;

    @Column(name = "consumed_ref_id")
    private Long consumedRefId;
}
