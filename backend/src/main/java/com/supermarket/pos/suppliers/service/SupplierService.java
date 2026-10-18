package com.supermarket.pos.suppliers.service;

import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.web.PageRequests;
import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.purchases.dto.PurchaseDtos.SupplierDto;
import com.supermarket.pos.purchases.dto.PurchaseDtos.SupplierRequest;
import com.supermarket.pos.suppliers.entity.Supplier;
import com.supermarket.pos.suppliers.repository.SupplierRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SupplierService {

    private static final Map<String, String> SORTS = Map.of("name", "name", "code", "code");

    private final SupplierRepository suppliers;
    private final AuditService audit;

    public SupplierService(SupplierRepository suppliers, AuditService audit) {
        this.suppliers = suppliers;
        this.audit = audit;
    }

    @PreAuthorize("hasAnyAuthority('SUPPLIER_MANAGE','PURCHASE_ORDER_MANAGE','INVENTORY_RECEIVE','INVENTORY_VIEW')")
    @Transactional(readOnly = true)
    public PageResponse<SupplierDto> search(String q, Boolean active, Integer page, Integer size, List<String> sort) {
        var pageable = PageRequests.of(page, size, sort, SORTS, Sort.by("name"));
        return PageResponse.of(suppliers.search(q == null || q.isBlank() ? null : q.trim(), active, pageable),
                SupplierService::toDto);
    }

    @PreAuthorize("hasAnyAuthority('SUPPLIER_MANAGE','PURCHASE_ORDER_MANAGE','INVENTORY_RECEIVE','INVENTORY_VIEW')")
    @Transactional(readOnly = true)
    public SupplierDto get(Long id) {
        return toDto(require(id));
    }

    @PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
    @Transactional
    public SupplierDto create(SupplierRequest request) {
        if (suppliers.existsByCodeIgnoreCase(request.code())) {
            throw ApiException.duplicate("code", "Supplier code " + request.code() + " already exists.");
        }
        Supplier s = new Supplier();
        s.setCode(request.code());
        apply(s, request);
        Supplier saved = suppliers.saveAndFlush(s);
        audit.record(AuditService.Entry.success("SUPPLIER_CREATED", "Supplier", saved.getId(), saved.getName()));
        return toDto(saved);
    }

    @PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
    @Transactional
    public SupplierDto update(Long id, SupplierRequest request) {
        Supplier s = require(id);
        if (request.version() == null || s.getVersion() != request.version()) {
            throw new OptimisticLockingFailureException("Supplier changed");
        }
        if (!s.getCode().equalsIgnoreCase(request.code()) && suppliers.existsByCodeIgnoreCase(request.code())) {
            throw ApiException.duplicate("code", "Supplier code " + request.code() + " already exists.");
        }
        s.setCode(request.code());
        apply(s, request);
        audit.record(AuditService.Entry.success("SUPPLIER_UPDATED", "Supplier", id, s.getName()));
        return toDto(suppliers.saveAndFlush(s));
    }

    @Transactional(readOnly = true)
    public Supplier require(Long id) {
        return suppliers.findById(id).orElseThrow(() -> ApiException.notFound("Supplier", id));
    }

    @Transactional(readOnly = true)
    public Map<Long, String> names(Collection<Long> ids) {
        return suppliers.findAllById(ids).stream().collect(Collectors.toMap(Supplier::getId, Supplier::getName));
    }

    private static void apply(Supplier s, SupplierRequest r) {
        s.setName(r.name().trim());
        s.setContactPerson(blank(r.contactPerson()));
        s.setPhone(blank(r.phone()));
        s.setEmail(blank(r.email()));
        s.setAddress(blank(r.address()));
        s.setTaxRegistrationNo(blank(r.taxRegistrationNo()));
        s.setPaymentTermsDays(r.paymentTermsDays());
        s.setNotes(blank(r.notes()));
        s.setActive(r.active());
    }

    public static SupplierDto toDto(Supplier s) {
        return new SupplierDto(s.getId(), s.getCode(), s.getName(), s.getContactPerson(), s.getPhone(), s.getEmail(),
                s.getAddress(), s.getTaxRegistrationNo(), s.getPaymentTermsDays(), s.getNotes(), s.isActive(), s.getVersion());
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
