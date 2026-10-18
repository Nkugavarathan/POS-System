package com.supermarket.pos.pos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.auth.entity.ApprovalAction;
import com.supermarket.pos.auth.service.ApprovalService;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.idempotency.IdempotencyRecord;
import com.supermarket.pos.common.idempotency.IdempotencyService;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.common.security.CurrentUser;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.common.sequence.SequenceService;
import com.supermarket.pos.common.web.PageRequests;
import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.pos.dto.PosDtos.CashMovementDto;
import com.supermarket.pos.pos.dto.PosDtos.CashMovementRequest;
import com.supermarket.pos.pos.dto.PosDtos.CloseShiftRequest;
import com.supermarket.pos.pos.dto.PosDtos.ShiftDto;
import com.supermarket.pos.pos.dto.PosDtos.ShiftSummaryDto;
import com.supermarket.pos.pos.dto.PosDtos.TenderTotal;
import com.supermarket.pos.pos.entity.CashMovement;
import com.supermarket.pos.pos.entity.CashierShift;
import com.supermarket.pos.pos.entity.HeldCart;
import com.supermarket.pos.pos.repository.CashMovementRepository;
import com.supermarket.pos.pos.repository.CashierShiftRepository;
import com.supermarket.pos.pos.repository.HeldCartRepository;
import com.supermarket.pos.pos.repository.TransactionVoidRepository;
import com.supermarket.pos.store.entity.Register;
import com.supermarket.pos.store.entity.Store;
import com.supermarket.pos.store.service.SettingsService;
import com.supermarket.pos.store.service.StoreService;
import com.supermarket.pos.users.service.UserAccountService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Cashier shifts and the cash drawer. Expected cash at close =
 * opening float + cash taken (net of change) − cash refunds + pay-ins − pay-outs − cash drops.
 */
@Service
public class ShiftService {

    private final CashierShiftRepository shifts;
    private final CashMovementRepository cashMovements;
    private final HeldCartRepository heldCarts;
    private final TransactionVoidRepository voids;
    /** Looked up lazily: activity providers (sales, returns) themselves depend on this service. */
    private final ObjectProvider<ShiftActivity> activities;
    private final StoreService stores;
    private final SettingsService settings;
    private final SequenceService sequences;
    private final ApprovalService approvals;
    private final IdempotencyService idempotency;
    private final UserAccountService users;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate tx;

    public ShiftService(CashierShiftRepository shifts, CashMovementRepository cashMovements,
                        HeldCartRepository heldCarts, TransactionVoidRepository voids, ObjectProvider<ShiftActivity> activities,
                        StoreService stores, SettingsService settings, SequenceService sequences,
                        ApprovalService approvals, IdempotencyService idempotency, UserAccountService users,
                        CurrentUserProvider currentUser, AuditService audit, ObjectMapper objectMapper, Clock clock,
                        PlatformTransactionManager txManager) {
        this.shifts = shifts;
        this.cashMovements = cashMovements;
        this.heldCarts = heldCarts;
        this.voids = voids;
        this.activities = activities;
        this.stores = stores;
        this.settings = settings;
        this.sequences = sequences;
        this.approvals = approvals;
        this.idempotency = idempotency;
        this.users = users;
        this.currentUser = currentUser;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    // ----- API for checkout / returns --------------------------------------------------------

    /**
     * The caller's OPEN shift on the given register, locked FOR UPDATE so the shift cannot be
     * closed while a sale or refund is being written. Must run inside the business transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CashierShift lockOpenShift(Long userId, Long storeId, Long registerId) {
        CashierShift shift = shifts.findFirstByCashierIdAndStatus(userId, CashierShift.Status.OPEN)
                .orElseThrow(() -> new ApiException(ErrorCode.SHIFT_NOT_OPEN, "Open a shift before selling."));
        CashierShift locked = shifts.lockById(shift.getId()).orElseThrow();
        if (locked.getStatus() != CashierShift.Status.OPEN) {
            throw new ApiException(ErrorCode.SHIFT_NOT_OPEN, "Your shift has been closed.");
        }
        if (!locked.getStoreId().equals(storeId) || (registerId != null && !locked.getRegisterId().equals(registerId))) {
            throw new ApiException(ErrorCode.SHIFT_NOT_OPEN, "Your open shift is on another register or store.",
                    Map.of("shiftRegisterId", locked.getRegisterId()));
        }
        return locked;
    }

    // ----- shift lifecycle -------------------------------------------------------------------

    @PreAuthorize("hasAuthority('SHIFT_OPERATE')")
    @Transactional(readOnly = true)
    public Optional<ShiftDto> current() {
        CurrentUser user = currentUser.require();
        return shifts.findFirstByCashierIdAndStatus(user.id(), CashierShift.Status.OPEN).map(this::toDto);
    }

    @PreAuthorize("hasAuthority('SHIFT_OPERATE')")
    public ShiftDto open(Long registerId, BigDecimal openingFloat) {
        CurrentUser user = currentUser.require();
        Long storeId = user.requireActiveStore();
        try {
            return tx.execute(status -> {
                Register register = stores.requireActiveRegister(storeId, registerId);
                Store store = stores.requireStore(storeId);
                Instant now = clock.instant();
                if (shifts.findFirstByCashierIdAndStatus(user.id(), CashierShift.Status.OPEN).isPresent()) {
                    throw new ApiException(ErrorCode.SHIFT_ALREADY_OPEN, "You already have an open shift.");
                }
                shifts.findFirstByRegisterIdAndStatus(registerId, CashierShift.Status.OPEN).ifPresent(s -> {
                    throw new ApiException(ErrorCode.SHIFT_ALREADY_OPEN, "Register " + register.getCode()
                            + " already has an open shift.", Map.of("cashierId", s.getCashierId()));
                });
                CashierShift shift = new CashierShift();
                shift.setStoreId(storeId);
                shift.setRegisterId(registerId);
                shift.setCashierId(user.id());
                shift.setOpenedAt(now);
                shift.setOpeningFloat(MoneyPolicy.amount(openingFloat));
                shift.setShiftNumber(sequences.nextDailyNumber("SHIFT", store.getCode() + "-" + register.getCode(),
                        stores.businessDate(store, now), 2));
                CashierShift saved = shifts.saveAndFlush(shift);
                audit.record(AuditService.Entry.success("SHIFT_OPENED", "CashierShift", saved.getId(),
                        saved.getShiftNumber() + " float " + saved.getOpeningFloat()).withStore(storeId));
                return toDto(saved);
            });
        } catch (DataIntegrityViolationException race) {
            // Unique open-shift indexes: another session opened a shift at the same moment.
            throw new ApiException(ErrorCode.SHIFT_ALREADY_OPEN, "A shift is already open for you or this register.");
        }
    }

    public IdempotencyService.Outcome<CashMovementDto> cashMovement(Long shiftId, CashMovementRequest request, String key) {
        CurrentUser user = currentUser.require();
        if (!user.has("CASH_MOVEMENT")) {
            throw ApiException.forbidden("Recording cash movements requires CASH_MOVEMENT.");
        }
        IdempotencyService.Request idem = idempotency.request(user.id(), "CASH_MOVEMENT", key, Map.of("shiftId", shiftId, "body", request));
        return idempotency.execute(idem, () -> tx.execute(s -> recordCashMovement(shiftId, request, idem, user)),
                id -> toDto(cashMovements.findById(id).orElseThrow()));
    }

    private CashMovementDto recordCashMovement(Long shiftId, CashMovementRequest request, IdempotencyService.Request idem,
                                               CurrentUser user) {
        IdempotencyRecord record = idempotency.register(idem);
        CashierShift shift = shifts.lockById(shiftId).orElseThrow(() -> ApiException.notFound("Shift", shiftId));
        user.assertStoreAccess(shift.getStoreId(), "Shift", shiftId);
        if (shift.getStatus() != CashierShift.Status.OPEN) {
            throw new ApiException(ErrorCode.SHIFT_NOT_OPEN, "This shift is closed.");
        }
        if (!shift.getCashierId().equals(user.id()) && !user.has("SHIFT_VIEW_ALL")) {
            throw ApiException.forbidden("Only the shift's cashier or a manager can record cash movements.");
        }
        BigDecimal amount = MoneyPolicy.amount(request.amount());
        Long approvedBy = null;
        if (request.type() != CashMovement.Type.PAY_IN) {
            BigDecimal limit = settings.decimal(shift.getStoreId(), "cash.payoutApprovalThreshold", new BigDecimal("2000"));
            if (request.type() == CashMovement.Type.PAY_OUT && amount.compareTo(limit) > 0 && !user.has("CASH_MOVEMENT_APPROVE")) {
                if (request.approvalToken() == null || request.approvalToken().isBlank()) {
                    throw new ApiException(ErrorCode.APPROVAL_REQUIRED, "Pay-outs above " + limit + " need manager approval.",
                            Map.of("action", ApprovalAction.CASH_PAYOUT_OVER_LIMIT.name(), "value", amount.toPlainString()));
                }
                approvedBy = approvals.consume(request.approvalToken(), ApprovalAction.CASH_PAYOUT_OVER_LIMIT,
                        shift.getStoreId(), user.id(), amount).approverId();
            }
            BigDecimal available = computeExpectedCash(shift, summaryParts(shift));
            if (amount.compareTo(available) > 0) {
                throw ApiException.rule("The drawer should only hold " + available + "; cannot take out " + amount + ".");
            }
        }
        CashMovement m = new CashMovement();
        m.setShiftId(shiftId);
        m.setMovementType(request.type());
        m.setAmount(amount);
        m.setReason(request.reason().trim());
        m.setPerformedBy(user.id());
        m.setApprovedBy(approvedBy);
        m.setCreatedAt(clock.instant());
        CashMovement saved = cashMovements.saveAndFlush(m);
        audit.record(AuditService.Entry.success("CASH_" + request.type().name(), "CashierShift", shiftId,
                request.type() + " " + amount + ": " + m.getReason()).withStore(shift.getStoreId()));
        idempotency.complete(record, "CASH_MOVEMENT", saved.getId(), 201);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public ShiftSummaryDto summary(Long shiftId) {
        CurrentUser user = currentUser.require();
        CashierShift shift = shifts.findById(shiftId).orElseThrow(() -> ApiException.notFound("Shift", shiftId));
        user.assertStoreAccess(shift.getStoreId(), "Shift", shiftId);
        if (!shift.getCashierId().equals(user.id()) && !user.has("SHIFT_VIEW_ALL")) {
            throw ApiException.forbidden("You can only view your own shifts.");
        }
        return buildSummary(shift);
    }

    public IdempotencyService.Outcome<ShiftSummaryDto> close(Long shiftId, CloseShiftRequest request, String key) {
        CurrentUser user = currentUser.require();
        IdempotencyService.Request idem = idempotency.request(user.id(), "SHIFT_CLOSE", key, Map.of("shiftId", shiftId, "body", request));
        return idempotency.execute(idem, () -> tx.execute(s -> doClose(shiftId, request, idem, user)),
                id -> buildSummary(shifts.findById(id).orElseThrow()));
    }

    private ShiftSummaryDto doClose(Long shiftId, CloseShiftRequest request, IdempotencyService.Request idem, CurrentUser user) {
        IdempotencyRecord record = idempotency.register(idem);
        CashierShift shift = shifts.lockById(shiftId).orElseThrow(() -> ApiException.notFound("Shift", shiftId));
        user.assertStoreAccess(shift.getStoreId(), "Shift", shiftId);
        boolean own = shift.getCashierId().equals(user.id());
        if (!own && !(user.has("SHIFT_VIEW_ALL") && user.has("SHIFT_VARIANCE_APPROVE"))) {
            throw ApiException.forbidden("Only the cashier or a manager can close this shift.");
        }
        if (own && !user.has("SHIFT_OPERATE")) {
            throw ApiException.forbidden("Closing a shift requires SHIFT_OPERATE.");
        }
        if (shift.getStatus() != CashierShift.Status.OPEN) {
            throw ApiException.invalidState("This shift is already closed.");
        }
        Parts parts = summaryParts(shift);
        BigDecimal expected = computeExpectedCash(shift, parts);
        BigDecimal counted = MoneyPolicy.amount(request.countedCash());
        BigDecimal variance = counted.subtract(expected);
        BigDecimal threshold = settings.decimal(shift.getStoreId(), "shift.varianceApprovalThreshold", new BigDecimal("500"));
        if (variance.abs().compareTo(threshold) > 0 && !user.has("SHIFT_VARIANCE_APPROVE")) {
            if (request.approvalToken() == null || request.approvalToken().isBlank()) {
                throw new ApiException(ErrorCode.APPROVAL_REQUIRED, "The cash variance of " + variance
                        + " is above " + threshold + " and needs manager approval.",
                        Map.of("action", ApprovalAction.SHIFT_VARIANCE.name(), "value", variance.abs().toPlainString(),
                                "expectedCash", expected.toPlainString()));
            }
            shift.setVarianceApprovedBy(approvals.consume(request.approvalToken(), ApprovalAction.SHIFT_VARIANCE,
                    shift.getStoreId(), user.id(), variance.abs()).approverId());
        } else if (variance.abs().compareTo(threshold) > 0) {
            shift.setVarianceApprovedBy(user.id());
        }
        Instant now = clock.instant();
        shift.setStatus(CashierShift.Status.CLOSED);
        shift.setClosedAt(now);
        shift.setClosedBy(user.id());
        shift.setExpectedCash(expected);
        shift.setCountedCash(counted);
        shift.setVariance(variance);
        shift.setCloseNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        shift.setDenominationJson(toJson(request.denominations()));
        for (HeldCart cart : heldCarts.findByShiftIdAndStatus(shiftId, HeldCart.Status.HELD)) {
            cart.setStatus(HeldCart.Status.EXPIRED);
        }
        shifts.saveAndFlush(shift);
        audit.record(AuditService.Entry.success(own ? "SHIFT_CLOSED" : "SHIFT_FORCE_CLOSED", "CashierShift", shiftId,
                        shift.getShiftNumber() + " expected " + expected + ", counted " + counted + ", variance " + variance)
                .withStore(shift.getStoreId()));
        idempotency.complete(record, "SHIFT", shiftId, 200);
        return buildSummary(shift);
    }

    @Transactional(readOnly = true)
    public PageResponse<ShiftDto> list(Long cashierId, Long registerId, CashierShift.Status status, Instant from,
                                       Instant to, Integer page, Integer size) {
        CurrentUser user = currentUser.require();
        Long storeId = user.requireActiveStore();
        Long effectiveCashier = user.has("SHIFT_VIEW_ALL") ? cashierId : user.id();
        if (!user.has("SHIFT_VIEW_ALL") && !user.has("SHIFT_OPERATE")) {
            throw ApiException.forbidden("You cannot view shifts.");
        }
        Instant end = to == null ? clock.instant().plus(Duration.ofDays(1)) : to;
        Instant start = from == null ? end.minus(Duration.ofDays(31)) : from;
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "openedAt"));
        var result = shifts.search(storeId, effectiveCashier, registerId, status, start, end, pageable);
        Map<Long, String> names = users.displayNames(result.getContent().stream().map(CashierShift::getCashierId).toList());
        Map<Long, String> codes = stores.registerCodes(result.getContent().stream().map(CashierShift::getRegisterId).toList());
        return PageResponse.of(result, s -> toDto(s, names, codes));
    }

    // ----- computation -------------------------------------------------------------------------

    private record Parts(BigDecimal cashIn, BigDecimal cashOut, BigDecimal payIns, BigDecimal payOuts,
                         BigDecimal drops, Map<String, BigDecimal> tenderAmounts, Map<String, Long> tenderCounts,
                         Map<String, Long> counters, Map<String, BigDecimal> amounts, List<CashMovement> movements) {
    }

    private Parts summaryParts(CashierShift shift) {
        BigDecimal cashIn = BigDecimal.ZERO;
        BigDecimal cashOut = BigDecimal.ZERO;
        Map<String, BigDecimal> tenders = new TreeMap<>();
        Map<String, Long> tenderCounts = new HashMap<>();
        Map<String, Long> counters = new HashMap<>();
        Map<String, BigDecimal> amounts = new HashMap<>();
        for (ShiftActivity activity : activities.orderedStream().toList()) {
            ShiftActivity.Contribution c = activity.contributionFor(shift.getId());
            cashIn = cashIn.add(c.cashIn());
            cashOut = cashOut.add(c.cashOut());
            c.tenderAmounts().forEach((k, v) -> tenders.merge(k, v, BigDecimal::add));
            c.tenderCounts().forEach((k, v) -> tenderCounts.merge(k, v, Long::sum));
            c.counters().forEach((k, v) -> counters.merge(k, v, Long::sum));
            c.amounts().forEach((k, v) -> amounts.merge(k, v, BigDecimal::add));
        }
        List<CashMovement> movements = cashMovements.findByShiftIdOrderByIdAsc(shift.getId());
        BigDecimal payIns = sum(movements, CashMovement.Type.PAY_IN);
        BigDecimal payOuts = sum(movements, CashMovement.Type.PAY_OUT);
        BigDecimal drops = sum(movements, CashMovement.Type.CASH_DROP);
        return new Parts(cashIn, cashOut, payIns, payOuts, drops, tenders, tenderCounts, counters, amounts, movements);
    }

    private static BigDecimal computeExpectedCash(CashierShift shift, Parts p) {
        return MoneyPolicy.amount(shift.getOpeningFloat().add(p.cashIn()).subtract(p.cashOut())
                .add(p.payIns()).subtract(p.payOuts()).subtract(p.drops()));
    }

    private ShiftSummaryDto buildSummary(CashierShift shift) {
        Parts p = summaryParts(shift);
        BigDecimal expected = shift.getStatus() == CashierShift.Status.CLOSED && shift.getExpectedCash() != null
                ? shift.getExpectedCash() : computeExpectedCash(shift, p);
        List<TenderTotal> tenders = new ArrayList<>();
        p.tenderAmounts().forEach((k, v) -> tenders.add(new TenderTotal(k, MoneyPolicy.amount(v),
                p.tenderCounts().getOrDefault(k, 0L))));
        return new ShiftSummaryDto(toDto(shift), MoneyPolicy.amount(p.cashIn()), MoneyPolicy.amount(p.cashOut()),
                p.payIns(), p.payOuts(), p.drops(), expected, tenders,
                p.counters().getOrDefault("sales", 0L), p.counters().getOrDefault("returns", 0L),
                voids.countByShiftId(shift.getId()),
                MoneyPolicy.amount(p.amounts().getOrDefault("salesTotal", BigDecimal.ZERO)),
                MoneyPolicy.amount(p.amounts().getOrDefault("refundsTotal", BigDecimal.ZERO)),
                p.movements().stream().map(ShiftService::toDto).toList());
    }

    private static BigDecimal sum(List<CashMovement> movements, CashMovement.Type type) {
        return MoneyPolicy.amount(movements.stream().filter(m -> m.getMovementType() == type)
                .map(CashMovement::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private ShiftDto toDto(CashierShift s) {
        return toDto(s, users.displayNames(List.of(s.getCashierId())), stores.registerCodes(List.of(s.getRegisterId())));
    }

    private static ShiftDto toDto(CashierShift s, Map<Long, String> names, Map<Long, String> codes) {
        return new ShiftDto(s.getId(), s.getShiftNumber(), s.getStoreId(), s.getRegisterId(), codes.get(s.getRegisterId()),
                s.getCashierId(), names.get(s.getCashierId()), s.getStatus(), s.getOpenedAt(), s.getOpeningFloat(),
                s.getClosedAt(), s.getExpectedCash(), s.getCountedCash(), s.getVariance(), s.getVersion());
    }

    private static CashMovementDto toDto(CashMovement m) {
        return new CashMovementDto(m.getId(), m.getMovementType(), m.getAmount(), m.getReason(), m.getPerformedBy(),
                m.getApprovedBy(), m.getCreatedAt());
    }

    private String toJson(Map<String, Integer> denominations) {
        if (denominations == null || denominations.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(denominations);
            return json.length() > 1000 ? null : json;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
