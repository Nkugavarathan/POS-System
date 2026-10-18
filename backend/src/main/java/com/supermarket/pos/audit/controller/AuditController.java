package com.supermarket.pos.audit.controller;

import com.supermarket.pos.audit.dto.AuditDtos.AuditEntryDto;
import com.supermarket.pos.audit.entity.AuditOutcome;
import com.supermarket.pos.audit.service.AuditQueryService;
import com.supermarket.pos.common.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "Audit log")
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditQueryService audit;

    public AuditController(AuditQueryService audit) {
        this.audit = audit;
    }

    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    @GetMapping
    public PageResponse<AuditEntryDto> search(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                              @RequestParam(required = false) String action,
                                              @RequestParam(required = false) String actor,
                                              @RequestParam(required = false) String entityType,
                                              @RequestParam(required = false) String entityId,
                                              @RequestParam(required = false) AuditOutcome outcome,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size) {
        return audit.search(from, to, action, actor, entityType, entityId, outcome, page, size);
    }
}
