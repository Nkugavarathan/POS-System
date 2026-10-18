package com.supermarket.pos.common.error;

import com.supermarket.pos.common.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Builds RFC 9457 problem details with the POS extensions (code, correlationId, timestamp,
 * fieldErrors, details). Used by the MVC exception handler and by the security entry points.
 */
public final class ProblemFactory {

    public static final String TYPE_BASE = "https://pos.local/problems/";

    private ProblemFactory() {
    }

    public static ProblemDetail create(ErrorCode code, String detail, String instance) {
        return create(code, code.status(), detail, instance, Map.of(), List.of());
    }

    public static ProblemDetail create(ErrorCode code, HttpStatusCode status, String detail, String instance,
                                       Map<String, Object> details, List<FieldErrorDto> fieldErrors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail != null ? detail : code.title());
        problem.setType(URI.create(TYPE_BASE + code.slug()));
        problem.setTitle(code.title());
        if (instance != null) {
            problem.setInstance(URI.create(instance));
        }
        problem.setProperty("code", code.name());
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        problem.setProperty("timestamp", Instant.now(Clock.systemUTC()).toString());
        problem.setProperty("fieldErrors", fieldErrors == null ? List.of() : fieldErrors);
        if (details != null && !details.isEmpty()) {
            problem.setProperty("details", details);
        }
        return problem;
    }

    public record FieldErrorDto(String field, String message, Object rejectedValue) {
    }
}
