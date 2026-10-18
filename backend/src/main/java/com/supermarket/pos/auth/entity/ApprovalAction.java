package com.supermarket.pos.auth.entity;

/**
 * Actions that can require a manager approval, with the permission the approver must hold.
 * For value-limited actions the approval records the maximum value it covers.
 */
public enum ApprovalAction {

    /** value = discount percent; the approver's own role limit caps it. */
    DISCOUNT_OVER_LIMIT("DISCOUNT_APPROVE", LimitType.APPROVER_DISCOUNT_PERCENT),
    PRICE_OVERRIDE("PRICE_OVERRIDE", LimitType.NONE),
    LINE_VOID("VOID_APPROVE", LimitType.NONE),
    CART_VOID("VOID_APPROVE", LimitType.NONE),
    RETURN("RETURN_APPROVE", LimitType.NONE),
    /** value = pay-out amount. */
    CASH_PAYOUT_OVER_LIMIT("CASH_MOVEMENT_APPROVE", LimitType.REQUESTED_VALUE),
    /** value = absolute cash variance. */
    SHIFT_VARIANCE("SHIFT_VARIANCE_APPROVE", LimitType.REQUESTED_VALUE),
    /** value = write-off cost value. */
    WRITE_OFF_OVER_LIMIT("INVENTORY_APPROVE", LimitType.REQUESTED_VALUE);

    public enum LimitType { NONE, REQUESTED_VALUE, APPROVER_DISCOUNT_PERCENT }

    private final String requiredPermission;
    private final LimitType limitType;

    ApprovalAction(String requiredPermission, LimitType limitType) {
        this.requiredPermission = requiredPermission;
        this.limitType = limitType;
    }

    public String requiredPermission() {
        return requiredPermission;
    }

    public LimitType limitType() {
        return limitType;
    }
}
