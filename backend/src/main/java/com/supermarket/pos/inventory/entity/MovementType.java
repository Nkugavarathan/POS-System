package com.supermarket.pos.inventory.entity;

public enum MovementType {
    OPENING_BALANCE,
    PURCHASE_RECEIPT,
    SALE,
    SALE_RETURN,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT,
    WRITE_OFF_DAMAGED,
    WRITE_OFF_EXPIRED,
    COUNT_CORRECTION,
    /** Reserved for inter-store transfers (backlog). */
    TRANSFER_OUT,
    TRANSFER_IN
}
