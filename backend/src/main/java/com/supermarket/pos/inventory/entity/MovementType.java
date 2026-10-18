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
    /** Picked off the shelf for an online order (when packing starts). */
    ONLINE_ORDER_PICK,
    /** Picked stock put back because the online order was cancelled or not collected. */
    ONLINE_ORDER_RELEASE,
    /** Reserved for inter-store transfers (backlog). */
    TRANSFER_OUT,
    TRANSFER_IN
}
