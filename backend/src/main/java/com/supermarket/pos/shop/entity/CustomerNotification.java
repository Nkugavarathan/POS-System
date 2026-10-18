package com.supermarket.pos.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** In-app notification shown in the online shop (bell and inbox). */
@Getter
@Setter
@Entity
@Table(name = "customer_notifications")
public class CustomerNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_account_id", nullable = false, updatable = false)
    private Long customerAccountId;

    @Column(name = "online_order_id", updatable = false)
    private Long onlineOrderId;

    /** ORDER, SUBSTITUTION (needs a decision), ACCOUNT or DEAL. */
    @Column(nullable = false, length = 20, updatable = false)
    private String kind = "ORDER";

    @Column(nullable = false, length = 120, updatable = false)
    private String title;

    @Column(nullable = false, length = 500, updatable = false)
    private String body;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
