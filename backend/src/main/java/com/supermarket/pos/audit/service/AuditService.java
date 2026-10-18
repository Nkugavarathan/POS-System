package com.supermarket.pos.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.pos.audit.entity.AuditLog;
import com.supermarket.pos.audit.entity.AuditOutcome;
import com.supermarket.pos.audit.repository.AuditLogRepository;
import com.supermarket.pos.common.security.CurrentUser;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

/**
 * Writes the audit trail. {@link #record} joins the caller's transaction, so an audited
 * change and its audit row commit (or roll back) together. {@link #recordIndependently} is
 * for denials and failures whose surrounding transaction rolls back.
 * Never put secrets or full personal data into {@code details}.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, CurrentUserProvider currentUserProvider,
                        ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** One audit entry. {@code actorId}/{@code actorUsername} override the authenticated user (e.g. during login). */
    public record Entry(String action, String entityType, Object entityId, AuditOutcome outcome, String summary,
                        Map<String, ?> details, Long storeId, Long actorId, String actorUsername) {

        public static Entry success(String action, String entityType, Object entityId, String summary) {
            return new Entry(action, entityType, entityId, AuditOutcome.SUCCESS, summary, null, null, null, null);
        }

        public Entry withDetails(Map<String, ?> newDetails) {
            return new Entry(action, entityType, entityId, outcome, summary, newDetails, storeId, actorId, actorUsername);
        }

        public Entry withStore(Long newStoreId) {
            return new Entry(action, entityType, entityId, outcome, summary, details, newStoreId, actorId, actorUsername);
        }

        public Entry withActor(Long id, String username) {
            return new Entry(action, entityType, entityId, outcome, summary, details, storeId, id, username);
        }

        public Entry withOutcome(AuditOutcome newOutcome) {
            return new Entry(action, entityType, entityId, newOutcome, summary, details, storeId, actorId, actorUsername);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(Entry entry) {
        repository.save(toEntity(entry));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(Entry entry) {
        repository.save(toEntity(entry));
    }

    private AuditLog toEntity(Entry entry) {
        Optional<CurrentUser> user = currentUserProvider.current();
        AuditLog log = new AuditLog();
        log.setOccurredAt(clock.instant());
        log.setAction(entry.action());
        log.setEntityType(entry.entityType());
        log.setEntityId(entry.entityId() == null ? null : truncate(String.valueOf(entry.entityId()), 60));
        log.setOutcome(entry.outcome() == null ? AuditOutcome.SUCCESS : entry.outcome());
        log.setSummary(truncate(entry.summary(), 500));
        log.setDetails(toJson(entry.details()));
        log.setActorUserId(entry.actorId() != null ? entry.actorId() : user.map(CurrentUser::id).orElse(null));
        log.setActorUsername(truncate(entry.actorUsername() != null ? entry.actorUsername()
                : user.map(CurrentUser::username).orElse(null), 50));
        log.setStoreId(entry.storeId() != null ? entry.storeId() : user.map(CurrentUser::activeStoreId).orElse(null));
        log.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
        currentRequest().ifPresent(request -> {
            log.setIpAddress(truncate(request.getRemoteAddr(), 45));
            log.setUserAgent(truncate(request.getHeader("User-Agent"), 255));
        });
        return log;
    }

    private String toJson(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"details not serializable\"}";
        }
    }

    private static Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return Optional.of(attrs.getRequest());
        }
        return Optional.empty();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
