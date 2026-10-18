package com.supermarket.pos.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single domain exception type. Services throw it with a stable {@link ErrorCode}, a
 * human-readable detail that is safe to show to users, and optional structured details.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> details;

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, Map.of());
    }

    public ApiException(ErrorCode code, String detail, Map<String, Object> details) {
        super(detail);
        this.code = code;
        this.details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public static ApiException notFound(String entity, Object id) {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, entity + " not found",
                Map.of("entity", entity, "id", String.valueOf(id)));
    }

    public static ApiException duplicate(String field, String message) {
        return new ApiException(ErrorCode.DUPLICATE_RESOURCE, message, Map.of("field", field));
    }

    public static ApiException rule(String message) {
        return new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    public static ApiException invalidState(String message) {
        return new ApiException(ErrorCode.INVALID_STATE_TRANSITION, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.ACCESS_DENIED, message);
    }
}
