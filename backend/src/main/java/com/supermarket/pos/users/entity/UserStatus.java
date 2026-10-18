package com.supermarket.pos.users.entity;

public enum UserStatus {
    ACTIVE,
    /** Locked by an administrator (temporary lockouts after failed logins use locked_until instead). */
    LOCKED,
    DISABLED
}
