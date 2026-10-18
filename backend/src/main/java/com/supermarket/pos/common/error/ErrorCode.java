package com.supermarket.pos.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, documented error codes. The code (not the message) is the contract the frontend
 * relies on; see docs/04-api-contracts.md.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    INVALID_SORT_FIELD(HttpStatus.BAD_REQUEST, "Invalid sort field"),
    INVALID_BARCODE(HttpStatus.BAD_REQUEST, "Invalid barcode"),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency key required"),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Access token expired"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid username or password"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Session expired"),

    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Account locked"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Account disabled"),
    APPROVAL_REQUIRED(HttpStatus.FORBIDDEN, "Manager approval required"),
    APPROVAL_INVALID(HttpStatus.FORBIDDEN, "Approval is invalid or expired"),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "Invalid CSRF token"),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "Password change required"),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    BARCODE_NOT_FOUND(HttpStatus.NOT_FOUND, "Barcode not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),

    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Duplicate resource"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "The record was changed by someone else"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Insufficient stock"),
    QUOTE_MISMATCH(HttpStatus.CONFLICT, "Totals changed since the quote"),
    SHIFT_ALREADY_OPEN(HttpStatus.CONFLICT, "A shift is already open"),
    SHIFT_NOT_OPEN(HttpStatus.CONFLICT, "No open shift"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Invalid state transition"),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "The same request is still being processed"),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "Data integrity violation"),

    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violation"),
    PRODUCT_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "Product is inactive"),
    INSUFFICIENT_TENDER(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient tender"),
    PAYMENT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid payment"),
    RETURN_QUANTITY_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Return quantity exceeds returnable quantity"),
    RETURN_WINDOW_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Return window expired"),
    REFUND_EXCEEDS_PAID(HttpStatus.UNPROCESSABLE_ENTITY, "Refund exceeds amount paid"),
    REFUND_AMOUNT_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Refund amount mismatch"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reused with a different request"),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** Kebab-case slug used in the problem "type" URI. */
    public String slug() {
        return name().toLowerCase().replace('_', '-');
    }
}
