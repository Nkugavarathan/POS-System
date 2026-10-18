package com.supermarket.pos.shop.service;

/**
 * Delivery boundary for email and SMS. The default implementation only logs: connecting an
 * email service (SMTP, SES, ...) or an SMS gateway means providing another bean of this type.
 */
public interface NotificationSender {

    /** @throws Exception when delivery failed; the outbox retries up to 5 times. */
    void send(String channel, String recipient, String subject, String body) throws Exception;
}
