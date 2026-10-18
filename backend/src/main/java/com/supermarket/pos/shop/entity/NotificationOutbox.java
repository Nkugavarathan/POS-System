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

/** Email/SMS waiting to be delivered (transactional outbox, processed by a scheduler). */
@Getter
@Setter
@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false, updatable = false)
    private Long notificationId;

    @Column(nullable = false, length = 10, updatable = false)
    private String channel;

    @Column(nullable = false, length = 120, updatable = false)
    private String recipient;

    @Column(nullable = false, length = 120, updatable = false)
    private String subject;

    @Column(nullable = false, length = 500, updatable = false)
    private String body;

    @Column(nullable = false, length = 10)
    private String status = "PENDING";

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 255)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;
}
