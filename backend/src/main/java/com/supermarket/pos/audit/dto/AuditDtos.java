package com.supermarket.pos.audit.dto;

import com.supermarket.pos.audit.entity.AuditOutcome;

import java.time.Instant;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditEntryDto(Long id, Instant occurredAt, Long actorUserId, String actorUsername, Long storeId,
                                String action, String entityType, String entityId, AuditOutcome outcome, String summary,
                                String details, String ipAddress, String correlationId) {
    }
}
