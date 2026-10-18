package com.supermarket.pos.audit.service;

import com.supermarket.pos.audit.dto.AuditDtos.AuditEntryDto;
import com.supermarket.pos.audit.entity.AuditLog;
import com.supermarket.pos.audit.entity.AuditOutcome;
import com.supermarket.pos.audit.repository.AuditLogRepository;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.common.web.PageRequests;
import com.supermarket.pos.common.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read side of the audit log: the active store's entries plus system-wide ones (no store). */
@Service
public class AuditQueryService {

    private final AuditLogRepository repository;
    private final CurrentUserProvider currentUser;

    public AuditQueryService(AuditLogRepository repository, CurrentUserProvider currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEntryDto> search(Instant from, Instant to, String action, String actor, String entityType,
                                              String entityId, AuditOutcome outcome, Integer page, Integer size) {
        Long storeId = currentUser.require().requireActiveStore();
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.or(cb.equal(root.get("storeId"), storeId), cb.isNull(root.get("storeId"))));
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("occurredAt"), to));
            }
            if (action != null && !action.isBlank()) {
                p.add(cb.like(root.get("action"), "%" + escape(action.trim().toUpperCase(Locale.ROOT)) + "%", '!'));
            }
            if (actor != null && !actor.isBlank()) {
                p.add(cb.like(cb.lower(root.get("actorUsername")), "%" + escape(actor.trim().toLowerCase(Locale.ROOT)) + "%", '!'));
            }
            if (entityType != null && !entityType.isBlank()) {
                p.add(cb.equal(root.get("entityType"), entityType.trim()));
            }
            if (entityId != null && !entityId.isBlank()) {
                p.add(cb.equal(root.get("entityId"), entityId.trim()));
            }
            if (outcome != null) {
                p.add(cb.equal(root.get("outcome"), outcome));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.of(repository.findAll(spec, pageable), a -> new AuditEntryDto(a.getId(), a.getOccurredAt(),
                a.getActorUserId(), a.getActorUsername(), a.getStoreId(), a.getAction(), a.getEntityType(), a.getEntityId(),
                a.getOutcome(), a.getSummary(), a.getDetails(), a.getIpAddress(), a.getCorrelationId()));
    }

    /** LIKE pattern escaping with '!' as the escape character (no backslash ambiguity across databases). */
    private static String escape(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
