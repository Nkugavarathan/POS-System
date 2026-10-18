package com.supermarket.pos.store.service;

import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.security.CurrentUser;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.store.dto.StoreDtos.RegisterDto;
import com.supermarket.pos.store.dto.StoreDtos.RegisterRequest;
import com.supermarket.pos.store.dto.StoreDtos.StoreDto;
import com.supermarket.pos.store.dto.StoreDtos.StoreSummaryDto;
import com.supermarket.pos.store.dto.StoreDtos.TenderTypeDto;
import com.supermarket.pos.store.dto.StoreDtos.UpdateStoreRequest;
import com.supermarket.pos.store.entity.Register;
import com.supermarket.pos.store.entity.Store;
import com.supermarket.pos.store.entity.TenderType;
import com.supermarket.pos.store.repository.RegisterRepository;
import com.supermarket.pos.store.repository.StoreRepository;
import com.supermarket.pos.store.repository.TenderTypeRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StoreService {

    private final StoreRepository stores;
    private final RegisterRepository registers;
    private final TenderTypeRepository tenderTypes;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;

    public StoreService(StoreRepository stores, RegisterRepository registers, TenderTypeRepository tenderTypes,
                        CurrentUserProvider currentUser, AuditService audit) {
        this.stores = stores;
        this.registers = registers;
        this.tenderTypes = tenderTypes;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    // ----- API for other modules -----------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<Long, StoreSummaryDto> summaries(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return stores.findByIdInOrderByNameAsc(ids).stream()
                .map(StoreService::toSummary)
                .collect(Collectors.toMap(StoreSummaryDto::id, Function.identity()));
    }

    /** The store entity, for modules that snapshot store data (receipts) or need its timezone. */
    @Transactional(readOnly = true)
    public Store requireStore(Long storeId) {
        return stores.findById(storeId).orElseThrow(() -> ApiException.notFound("Store", storeId));
    }

    /** An active register of the given store. */
    @Transactional(readOnly = true)
    public Register requireActiveRegister(Long storeId, Long registerId) {
        Register register = registers.findByIdAndStoreId(registerId, storeId)
                .orElseThrow(() -> ApiException.notFound("Register", registerId));
        if (!register.isActive()) {
            throw ApiException.rule("Register " + register.getCode() + " is inactive.");
        }
        return register;
    }

    /** register id → register code. */
    @Transactional(readOnly = true)
    public Map<Long, String> registerCodes(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return registers.findAllById(new java.util.HashSet<>(ids)).stream()
                .collect(Collectors.toMap(Register::getId, Register::getCode));
    }

    /** Today's business date in the store's timezone (receipt numbers, daily reports). */
    public LocalDate businessDate(Store store, java.time.Instant instant) {
        return instant.atZone(ZoneId.of(store.getTimezone())).toLocalDate();
    }

    // ----- Store profile -------------------------------------------------------------------

    /** Active stores (online shop pickup locations). */
    @Transactional(readOnly = true)
    public List<Store> activeStores() {
        return stores.findAll().stream().filter(Store::isActive)
                .sorted(java.util.Comparator.comparing(Store::getName)).toList();
    }

    @Transactional(readOnly = true)
    public List<StoreSummaryDto> accessibleStores() {
        CurrentUser user = currentUser.require();
        return stores.findByIdInOrderByNameAsc(user.storeIds()).stream().map(StoreService::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public StoreDto get(Long id) {
        CurrentUser user = currentUser.require();
        user.assertStoreAccess(id, "Store", id);
        return toDto(requireStore(id));
    }

    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    @Transactional
    public StoreDto update(Long id, UpdateStoreRequest request) {
        currentUser.require().assertStoreAccess(id, "Store", id);
        Store store = requireStore(id);
        checkVersion(store.getVersion(), request.version());
        store.setName(request.name().trim());
        store.setAddressLine1(blankToNull(request.addressLine1()));
        store.setAddressLine2(blankToNull(request.addressLine2()));
        store.setCity(blankToNull(request.city()));
        store.setPhone(blankToNull(request.phone()));
        store.setEmail(blankToNull(request.email()));
        store.setTaxRegistrationNo(blankToNull(request.taxRegistrationNo()));
        store.setPricesIncludeTax(request.pricesIncludeTax());
        store.setReceiptHeader(blankToNull(request.receiptHeader()));
        store.setReceiptFooter(blankToNull(request.receiptFooter()));
        audit.record(AuditService.Entry.success("STORE_UPDATED", "Store", id, "Store profile updated").withStore(id));
        return toDto(stores.saveAndFlush(store));
    }

    // ----- Registers -----------------------------------------------------------------------

    @PreAuthorize("hasAnyAuthority('POS_SELL','REGISTER_MANAGE','SHIFT_VIEW_ALL')")
    @Transactional(readOnly = true)
    public List<RegisterDto> registers(Long storeId) {
        currentUser.require().assertStoreAccess(storeId, "Store", storeId);
        return registers.findByStoreIdOrderByCodeAsc(storeId).stream().map(StoreService::toDto).toList();
    }

    @PreAuthorize("hasAuthority('REGISTER_MANAGE')")
    @Transactional
    public RegisterDto createRegister(Long storeId, RegisterRequest request) {
        currentUser.require().assertStoreAccess(storeId, "Store", storeId);
        if (registers.existsByStoreIdAndCodeIgnoreCase(storeId, request.code())) {
            throw ApiException.duplicate("code", "A register with code " + request.code() + " already exists.");
        }
        Register register = new Register();
        register.setStore(requireStore(storeId));
        register.setCode(request.code());
        register.setName(request.name().trim());
        register.setActive(request.active());
        Register saved = registers.saveAndFlush(register);
        audit.record(AuditService.Entry.success("REGISTER_CREATED", "Register", saved.getId(),
                "Register " + saved.getCode() + " created").withStore(storeId));
        return toDto(saved);
    }

    @PreAuthorize("hasAuthority('REGISTER_MANAGE')")
    @Transactional
    public RegisterDto updateRegister(Long storeId, Long registerId, RegisterRequest request) {
        currentUser.require().assertStoreAccess(storeId, "Store", storeId);
        Register register = registers.findByIdAndStoreId(registerId, storeId)
                .orElseThrow(() -> ApiException.notFound("Register", registerId));
        checkVersion(register.getVersion(), request.version());
        if (!register.getCode().equalsIgnoreCase(request.code())
                && registers.existsByStoreIdAndCodeIgnoreCase(storeId, request.code())) {
            throw ApiException.duplicate("code", "A register with code " + request.code() + " already exists.");
        }
        register.setCode(request.code());
        register.setName(request.name().trim());
        register.setActive(request.active());
        audit.record(AuditService.Entry.success("REGISTER_UPDATED", "Register", registerId,
                "Register " + register.getCode() + " updated").withStore(storeId));
        return toDto(registers.saveAndFlush(register));
    }

    @Transactional(readOnly = true)
    public List<TenderTypeDto> tenderTypes() {
        return tenderTypes.findAllByOrderBySortOrderAsc().stream().map(StoreService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public TenderType requireActiveTender(String code) {
        TenderType tender = tenderTypes.findById(code).orElseThrow(() -> ApiException.notFound("Tender type", code));
        if (!tender.isActive()) {
            throw ApiException.rule("Tender type " + code + " is not active.");
        }
        return tender;
    }

    // ----- mapping -------------------------------------------------------------------------

    public static StoreSummaryDto toSummary(Store s) {
        return new StoreSummaryDto(s.getId(), s.getCode(), s.getName());
    }

    public static StoreDto toDto(Store s) {
        return new StoreDto(s.getId(), s.getCode(), s.getName(), s.getAddressLine1(), s.getAddressLine2(), s.getCity(),
                s.getPhone(), s.getEmail(), s.getTaxRegistrationNo(), s.getCurrency(), s.getTimezone(),
                s.isPricesIncludeTax(), s.getReceiptHeader(), s.getReceiptFooter(), s.isActive(), s.getVersion());
    }

    private static RegisterDto toDto(Register r) {
        return new RegisterDto(r.getId(), r.getStore().getId(), r.getCode(), r.getName(), r.isActive(), r.getVersion());
    }

    private static TenderTypeDto toDto(TenderType t) {
        return new TenderTypeDto(t.getCode(), t.getName(), t.isCash(), t.isRequiresReference(), t.isAllowsChange(),
                t.isActive(), t.getSortOrder());
    }

    private static void checkVersion(long current, Long expected) {
        if (expected == null || current != expected) {
            throw new OptimisticLockingFailureException("Version mismatch");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
