package com.supermarket.pos.shop.entity;

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

/** One step of the order timeline shown to the customer and to staff. */
@Getter
@Setter
@Entity
@Table(name = "online_order_events")
public class OnlineOrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "online_order_id", nullable = false, updatable = false)
    private Long onlineOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private OnlineOrder.Status status;

    @Column(nullable = false, length = 255, updatable = false)
    private String message;

    @Column(name = "actor_type", nullable = false, length = 10, updatable = false)
    private String actorType;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
