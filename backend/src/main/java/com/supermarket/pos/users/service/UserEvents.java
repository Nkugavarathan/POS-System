package com.supermarket.pos.users.service;

/**
 * Events published (synchronously, inside the transaction) when a user's credentials or
 * status change, so the auth module can revoke sessions without users depending on auth.
 */
public final class UserEvents {

    private UserEvents() {
    }

    /** All active sessions of this user must be revoked. */
    public record SessionsInvalidated(Long userId, String reason) {
    }
}
