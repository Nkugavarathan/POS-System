package com.supermarket.pos.common.error;

import com.supermarket.pos.common.error.ProblemFactory.FieldErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Translates every exception into a consistent problem-details response. Stack traces and
 * internal messages (SQL, class names) never reach the client; they are logged with the
 * correlation id instead.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Set<String> SENSITIVE_FIELD_HINTS = Set.of("password", "secret", "pin", "token");

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest request) {
        if (ex.getCode().status().is5xxServerError()) {
            log.error("API error {}: {}", ex.getCode(), ex.getMessage(), ex);
        } else {
            log.debug("API error {}: {}", ex.getCode(), ex.getMessage());
        }
        ProblemDetail body = ProblemFactory.create(ex.getCode(), ex.getCode().status(), ex.getMessage(),
                request.getRequestURI(), ex.getDetails(), List.of());
        return ResponseEntity.status(ex.getCode().status()).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action.", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return problem(ErrorCode.UNAUTHENTICATED, "Authentication required.", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                              HttpServletRequest request) {
        log.info("Optimistic lock conflict: {}", ex.getMessage());
        return problem(ErrorCode.CONCURRENT_MODIFICATION,
                "This record was changed by someone else. Reload and try again.", request);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handlePessimisticLock(PessimisticLockingFailureException ex,
                                                               HttpServletRequest request) {
        log.warn("Lock acquisition failed: {}", ex.getMessage());
        return problem(ErrorCode.CONCURRENT_MODIFICATION,
                "The records are busy. Please retry.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        if (message.contains("duplicate entry")) {
            return problem(ErrorCode.DUPLICATE_RESOURCE, "A record with the same unique value already exists.", request);
        }
        return problem(ErrorCode.DATA_INTEGRITY_VIOLATION, "The change conflicts with existing data.", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        List<FieldErrorDto> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorDto(v.getPropertyPath().toString(), v.getMessage(),
                        safeValue(v.getPropertyPath().toString(), v.getInvalidValue())))
                .toList();
        ProblemDetail body = ProblemFactory.create(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                "The request contains invalid values.", request.getRequestURI(), Map.of(), errors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Quote the correlation id when reporting it.", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        List<FieldErrorDto> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(new FieldErrorDto(fe.getField(), fe.getDefaultMessage(),
                    safeValue(fe.getField(), fe.getRejectedValue())));
        }
        ex.getBindingResult().getGlobalErrors().forEach(ge ->
                errors.add(new FieldErrorDto(ge.getObjectName(), ge.getDefaultMessage(), null)));
        ProblemDetail body = ProblemFactory.create(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                "The request contains invalid values.", path(request), Map.of(), errors);
        return ResponseEntity.badRequest().headers(headers).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(@NonNull HandlerMethodValidationException ex,
                                                                            @NonNull HttpHeaders headers,
                                                                            @NonNull HttpStatusCode status,
                                                                            @NonNull WebRequest request) {
        List<FieldErrorDto> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(err ->
                    errors.add(new FieldErrorDto(name, err.getDefaultMessage(), safeValue(name, result.getArgument()))));
        });
        ProblemDetail body = ProblemFactory.create(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                "The request contains invalid values.", path(request), Map.of(), errors);
        return ResponseEntity.badRequest().headers(headers).body(body);
    }

    /** Enriches Spring MVC's own problem responses (404, 405, unreadable body, ...) with our fields. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(@NonNull Exception ex, @Nullable Object body,
                                                             @NonNull HttpHeaders headers,
                                                             @NonNull HttpStatusCode statusCode,
                                                             @NonNull WebRequest request) {
        ErrorCode code = codeFor(statusCode);
        String detail = switch (code) {
            case MALFORMED_REQUEST -> "The request could not be read. Check the JSON body, parameters and headers.";
            case RESOURCE_NOT_FOUND -> "No such endpoint or resource.";
            case METHOD_NOT_ALLOWED -> "This HTTP method is not supported for this endpoint.";
            default -> code.title();
        };
        if (statusCode.is5xxServerError()) {
            log.error("MVC error", ex);
        }
        ProblemDetail problem = ProblemFactory.create(code, statusCode, detail, path(request), Map.of(), List.of());
        return ResponseEntity.status(statusCode).headers(headers).body(problem);
    }

    private static ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400, 415, 406 -> ErrorCode.MALFORMED_REQUEST;
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.ACCESS_DENIED;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> ErrorCode.DATA_INTEGRITY_VIOLATION;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }

    private static ResponseEntity<ProblemDetail> problem(ErrorCode code, String detail, HttpServletRequest request) {
        return ResponseEntity.status(code.status()).body(ProblemFactory.create(code, detail, request.getRequestURI()));
    }

    private static String path(WebRequest request) {
        if (request instanceof ServletWebRequest servlet) {
            return servlet.getRequest().getRequestURI();
        }
        return null;
    }

    /** Never echo secrets back, and keep echoed values short. */
    private static Object safeValue(String field, Object value) {
        if (value == null || field == null) {
            return null;
        }
        String lower = field.toLowerCase(Locale.ROOT);
        if (SENSITIVE_FIELD_HINTS.stream().anyMatch(lower::contains)) {
            return null;
        }
        String text = String.valueOf(value);
        return text.length() > 100 ? text.substring(0, 100) + "…" : text;
    }
}
