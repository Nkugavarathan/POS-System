package com.supermarket.pos.returns.service;

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
import com.supermarket.pos.customers.entity.LoyaltyTransaction;
import com.supermarket.pos.customers.service.LoyaltyService;
import com.supermarket.pos.inventory.entity.MovementType;
import com.supermarket.pos.inventory.service.InventoryService;
import com.supermarket.pos.inventory.service.InventoryService.Receipt;
import com.supermarket.pos.inventory.service.InventoryService.Reference;
import com.supermarket.pos.payments.dto.PaymentDtos.PaymentDto;
import com.supermarket.pos.payments.service.PaymentService;
import com.supermarket.pos.pos.entity.CashierShift;
import com.supermarket.pos.pos.service.ShiftActivity;
import com.supermarket.pos.pos.service.ShiftService;
import com.supermarket.pos.products.entity.Product;
import com.supermarket.pos.products.service.ProductService;
import com.supermarket.pos.products.service.QuantityRules;
import com.supermarket.pos.returns.dto.ReturnDtos.PreviewDto;
import com.supermarket.pos.returns.dto.ReturnDtos.PreviewRequest;
import com.supermarket.pos.returns.dto.ReturnDtos.RefundDto;
import com.supermarket.pos.returns.dto.ReturnDtos.RefundRequest;
import com.supermarket.pos.returns.dto.ReturnDtos.ReturnDto;
import com.supermarket.pos.returns.dto.ReturnDtos.ReturnItemDto;
import com.supermarket.pos.returns.dto.ReturnDtos.ReturnLineRequest;
import com.supermarket.pos.returns.dto.ReturnDtos.ReturnRequest;
import com.supermarket.pos.returns.dto.ReturnDtos.TenderAllowance;
import com.supermarket.pos.returns.entity.Refund;
import com.supermarket.pos.returns.entity.ReturnItem;
import com.supermarket.pos.returns.entity.SaleReturn;
import com.supermarket.pos.returns.repository.RefundRepository;
import com.supermarket.pos.returns.repository.SaleReturnRepository;
import com.supermarket.pos.sales.dto.SalesDtos.SaleDto;
import com.supermarket.pos.sales.entity.Sale;
import com.supermarket.pos.sales.entity.SaleItem;
import com.supermarket.pos.sales.service.SaleQueryService;
import com.supermarket.pos.sales.service.SaleReturnSupport;
import com.supermarket.pos.sales.service.SaleReturnSupport.LineRefund;
import com.supermarket.pos.store.service.SettingsService;
import com.supermarket.pos.store.service.StoreService;
import com.supermarket.pos.users.service.UserAccountService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Returns and refunds against completed sales (W7). Bounds enforced here and in the database:
 * returned quantity ≤ sold, refunded amount ≤ line total, refunds per tender ≤ what that tender
 * paid. Saleable goods go back into stock (to their original batch); damaged/expired goods are
 * refunded without restocking.
 */
@Service
public class ReturnService implements ShiftActivity {

    public static final String REFERENCE_TYPE = "RETURN";

    private final SaleReturnRepository returns;
    private final RefundRepository refunds;
    private final SaleReturnSupport saleSupport;
    private final SaleQueryService saleQueries;
    private final PaymentService payments;
    private final InventoryService inventory;
    private final ProductService products;
    private final LoyaltyService loyalty;
    private final ShiftService shifts;
    private final ApprovalService approvals;
    private final SettingsService settings;
    private final StoreService stores;
    private final SequenceService sequences;
    private final IdempotencyService idempotency;
    private final UserAccountService users;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate tx;

    public ReturnService(SaleReturnRepository returns, RefundRepository refunds, SaleReturnSupport saleSupport,
                         SaleQueryService saleQueries, PaymentService payments, InventoryService inventory,
                         ProductService products, LoyaltyService loyalty, ShiftService shifts, ApprovalService approvals,
                         SettingsService settings, StoreService stores, SequenceService sequences,
                         IdempotencyService idempotency, UserAccountService users, CurrentUserProvider currentUser,
                         AuditService audit, Clock clock, PlatformTransactionManager txManager) {
        this.returns = returns;
        this.refunds = refunds;
        this.saleSupport = saleSupport;
        this.saleQueries = saleQueries;
        this.payments = payments;
        this.inventory = inventory;
        this.products = products;
        this.loyalty = loyalty;
        this.shifts = shifts;
        this.approvals = approvals;
        this.settings = settings;
        this.stores = stores;
        this.sequences = sequences;
        this.idempotency = idempotency;
        this.users = users;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    /** Computes the refund without writing anything (the screen shows it before confirming). */
    @Transactional
    public PreviewDto preview(PreviewRequest request) {
        CurrentUser user = requireReturnPermission();
        SaleDto saleDto = saleQueries.byReceipt(request.originalReceiptNumber());
        Sale sale = saleSupport.lock(saleDto.id());
        user.assertStoreAccess(sale.getStoreId(), "Sale", sale.getId());
        Computed c = compute(sale, request.items());
        // Read-only preview: roll back the lock transaction explicitly.
        org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        boolean approvalNeeded = settings.bool(sale.getStoreId(), "returns.approvalRequired", true) && !user.has("RETURN_APPROVE");
        return new PreviewDto(c.total(), c.tax(), c.items(), allowances(sale), approvalNeeded, withinWindow(sale),
                pointsToReverse(sale, c.total()));
    }

    public IdempotencyService.Outcome<ReturnDto> create(ReturnRequest request, String key) {
        CurrentUser user = requireReturnPermission();
        IdempotencyService.Request idem = idempotency.request(user.id(), "RETURN", key, request);
        return idempotency.execute(idem, () -> tx.execute(s -> doCreate(request, idem, user)), this::load);
    }

    private ReturnDto doCreate(ReturnRequest request, IdempotencyService.Request idem, CurrentUser user) {
        IdempotencyRecord record = idempotency.register(idem);
        Long storeId = user.requireActiveStore();
        SaleDto saleDto = saleQueries.byReceipt(request.originalReceiptNumber());
        Sale sale = saleSupport.lock(saleDto.id());
        user.assertStoreAccess(sale.getStoreId(), "Sale", sale.getId());
        if (!sale.getStoreId().equals(storeId)) {
            throw ApiException.rule("Returns are processed in the store where the sale was made.");
        }
        if (!withinWindow(sale) && !user.has("RETURN_APPROVE")) {
            throw new ApiException(ErrorCode.RETURN_WINDOW_EXPIRED, "This sale is outside the "
                    + settings.integer(storeId, "returns.windowDays", 14) + "-day return window.");
        }
        Long approvedBy = null;
        if (settings.bool(storeId, "returns.approvalRequired", true) && !user.has("RETURN_APPROVE")) {
            if (request.approvalToken() == null || request.approvalToken().isBlank()) {
                throw new ApiException(ErrorCode.APPROVAL_REQUIRED, "A manager must approve this return.",
                        Map.of("action", ApprovalAction.RETURN.name()));
            }
            approvedBy = approvals.consume(request.approvalToken(), ApprovalAction.RETURN, storeId, user.id(), null).approverId();
        } else if (user.has("RETURN_APPROVE")) {
            approvedBy = user.id();
        }
        CashierShift shift = shifts.lockOpenShift(user.id(), storeId, request.registerId());

        Computed computed = compute(sale, request.items());
        BigDecimal requested = MoneyPolicy.amount(request.refunds().stream().map(RefundRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        if (requested.compareTo(computed.total()) != 0) {
            throw new ApiException(ErrorCode.REFUND_AMOUNT_MISMATCH, "The refund must be exactly " + computed.total() + ".",
                    Map.of("expected", computed.total().toPlainString()));
        }
        Map<String, TenderAllowance> allowance = allowances(sale).stream()
                .collect(Collectors.toMap(TenderAllowance::tenderType, Function.identity()));
        Map<String, BigDecimal> perTender = new HashMap<>();
        for (RefundRequest r : request.refunds()) {
            perTender.merge(r.tenderType().toUpperCase(Locale.ROOT), MoneyPolicy.amount(r.amount()), BigDecimal::add);
        }
        BigDecimal cashAvailable = allowance.containsKey("CASH") ? allowance.get("CASH").available() : BigDecimal.ZERO;
        BigDecimal overflow = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : perTender.entrySet()) {
            TenderAllowance a = allowance.get(e.getKey());
            BigDecimal available = a == null ? BigDecimal.ZERO : a.available();
            if (e.getValue().compareTo(available) > 0) {
                if (!"CASH".equals(e.getKey())) {
                    // e.g. more back to card than the card paid
                    throw new ApiException(ErrorCode.REFUND_EXCEEDS_PAID, "At most " + available + " can be refunded to "
                            + e.getKey() + ".", Map.of("tenderType", e.getKey(), "available", available.toPlainString()));
                }
                overflow = e.getValue().subtract(available);
            }
        }
        if (overflow.signum() > 0 && !user.has("RETURN_APPROVE")) {
            // Refunding in cash what was paid by card needs a manager (approval above already covers it).
            if (approvedBy == null) {
                throw new ApiException(ErrorCode.REFUND_EXCEEDS_PAID, "Only " + cashAvailable + " was paid in cash.");
            }
        }

        Instant now = clock.instant();
        SaleReturn ret = new SaleReturn();
        ret.setStoreId(storeId);
        ret.setRegisterId(shift.getRegisterId());
        ret.setShiftId(shift.getId());
        ret.setOriginalSaleId(sale.getId());
        ret.setProcessedBy(user.id());
        ret.setApprovedBy(approvedBy);
        ret.setReasonCode(request.reasonCode().trim());
        ret.setNotes(request.notes());
        ret.setRefundTotal(computed.total());
        ret.setTaxTotal(computed.tax());
        ret.setCreatedAt(now);
        ret.setReturnNumber(sequences.nextNumber("RETURN", "RET", stores.requireStore(storeId).getCode(), 6));
        Map<Long, SaleItem> saleItems = sale.getItems().stream().collect(Collectors.toMap(SaleItem::getId, Function.identity()));
        for (int i = 0; i < request.items().size(); i++) {
            ReturnLineRequest line = request.items().get(i);
            ReturnItemDto c = computed.items().get(i);
            ReturnItem item = new ReturnItem();
            item.setSaleReturn(ret);
            item.setSaleItemId(line.saleItemId());
            item.setProductId(c.productId());
            item.setQuantity(c.quantity());
            item.setRefundAmount(c.refundAmount());
            item.setTaxAmount(c.taxAmount());
            item.setItemCondition(line.condition());
            item.setRestock(line.restock() && line.condition() == ReturnItem.Condition.SALEABLE);
            item.setReason(line.reason());
            ret.getItems().add(item);
            saleSupport.applyReturn(sale, saleItems.get(line.saleItemId()), c.quantity(), c.refundAmount());
        }
        SaleReturn saved = returns.saveAndFlush(ret);

        long pointsReversed = 0;
        List<Refund> refundRows = new ArrayList<>();
        for (RefundRequest r : request.refunds()) {
            String tender = stores.requireActiveTender(r.tenderType().toUpperCase(Locale.ROOT)).getCode();
            Refund refund = new Refund();
            refund.setReturnId(saved.getId());
            refund.setTenderType(tender);
            refund.setAmount(MoneyPolicy.amount(r.amount()));
            refund.setReference(r.reference());
            refund.setCardLast4(r.cardLast4());
            refund.setProcessedBy(user.id());
            refund.setCreatedAt(now);
            if ("LOYALTY".equals(tender)) {
                if (sale.getCustomerId() == null) {
                    throw ApiException.rule("This sale has no customer to refund points to.");
                }
                long points = LoyaltyService.pointsToCover(loyalty.rules(storeId), refund.getAmount());
                loyalty.post(sale.getCustomerId(), LoyaltyTransaction.Type.REDEEM_REVERSAL, points, REFERENCE_TYPE,
                        saved.getId(), "Refund " + saved.getReturnNumber(), user.id());
                refund.setLoyaltyPoints(points);
            }
            refundRows.add(refund);
        }
        refunds.saveAll(refundRows);

        // Restock saleable goods to the batch they were sold from, at the cost they were sold at.
        List<Receipt> restock = new ArrayList<>();
        for (ReturnItem item : saved.getItems()) {
            if (item.isRestock()) {
                SaleItem si = saleItems.get(item.getSaleItemId());
                Long batchId = inventory.batchOf("SALE", sale.getId(), si.getId()).orElse(null);
                restock.add(new Receipt(item.getProductId(), item.getQuantity(), si.getUnitCost(), null, null,
                        item.getId(), batchId, null));
            }
        }
        inventory.receive(storeId, MovementType.SALE_RETURN,
                new Reference(REFERENCE_TYPE, saved.getId(), user.id(), "Return " + saved.getReturnNumber()), restock);

        // Points earned on the refunded part of the sale are taken back.
        if (sale.getCustomerId() != null && sale.getLoyaltyPointsEarned() > 0) {
            long toReverse = pointsToReverse(sale, computed.total());
            if (toReverse > 0) {
                LoyaltyTransaction t = loyalty.post(sale.getCustomerId(), LoyaltyTransaction.Type.EARN_REVERSAL, -toReverse,
                        REFERENCE_TYPE, saved.getId(), "Return " + saved.getReturnNumber(), user.id());
                pointsReversed = t == null ? 0 : -t.getPoints();
            }
        }
        saved.setLoyaltyPointsReversed(pointsReversed);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sale", sale.getReceiptNumber());
        details.put("refundTotal", computed.total().toPlainString());
        details.put("tenders", perTender.keySet());
        if (approvedBy != null) {
            details.put("approvedBy", approvedBy);
        }
        audit.record(AuditService.Entry.success("RETURN_COMPLETED", "Return", saved.getId(), saved.getReturnNumber())
                .withStore(storeId).withDetails(details));
        idempotency.complete(record, "RETURN", saved.getId(), 201);
        return toDto(saved, sale.getReceiptNumber());
    }

    @Transactional(readOnly = true)
    public ReturnDto get(Long id) {
        CurrentUser user = currentUser.require();
        if (!user.hasAny("RETURN_CREATE", "SALE_VIEW_ALL")) {
            throw ApiException.forbidden("You cannot view returns.");
        }
        SaleReturn r = returns.findWithItemsById(id).orElseThrow(() -> ApiException.notFound("Return", id));
        user.assertStoreAccess(r.getStoreId(), "Return", id);
        return load(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReturnDto> list(Instant from, Instant to, Long saleId, Integer page, Integer size) {
        CurrentUser user = currentUser.require();
        if (!user.hasAny("RETURN_CREATE", "SALE_VIEW_ALL")) {
            throw ApiException.forbidden("You cannot view returns.");
        }
        Instant end = to == null ? clock.instant().plus(Duration.ofMinutes(1)) : to;
        Instant start = from == null ? end.minus(Duration.ofDays(30)) : from;
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(returns.search(user.requireActiveStore(), start, end, saleId, pageable),
                r -> toDto(r, null));
    }

    // ----- ShiftActivity (refunds pay cash out of the drawer) ---------------------------------

    @Override
    @Transactional(readOnly = true)
    public Contribution contributionFor(Long shiftId) {
        BigDecimal cashOut = BigDecimal.ZERO;
        for (Object[] row : returns.refundTendersForShift(shiftId)) {
            if ("CASH".equals(row[0])) {
                cashOut = (BigDecimal) row[2];
            }
        }
        Object[] totals = returns.shiftTotals(shiftId).get(0);
        return new Contribution(BigDecimal.ZERO, cashOut, Map.of(), Map.of(), Map.of("returns", (Long) totals[0]),
                Map.of("refundsTotal", (BigDecimal) totals[1]));
    }

    // ----- helpers -----------------------------------------------------------------------------

    private record Computed(BigDecimal total, BigDecimal tax, List<ReturnItemDto> items) {
    }

    private Computed compute(Sale sale, List<ReturnLineRequest> lines) {
        Map<Long, SaleItem> items = sale.getItems().stream().collect(Collectors.toMap(SaleItem::getId, Function.identity()));
        Map<Long, Product> productMap = products.loadForPricing(sale.getItems().stream().map(SaleItem::getProductId).toList());
        Map<Long, BigDecimal> requestedPerItem = new HashMap<>();
        List<ReturnItemDto> result = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (ReturnLineRequest line : lines) {
            SaleItem item = items.get(line.saleItemId());
            if (item == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Item " + line.saleItemId() + " is not on this receipt.");
            }
            Product product = productMap.get(item.getProductId());
            BigDecimal qty = product == null ? MoneyPolicy.quantity(line.quantity()) : QuantityRules.validate(product, line.quantity());
            BigDecimal already = requestedPerItem.merge(item.getId(), qty, BigDecimal::add);
            if (already.compareTo(qty) != 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "List each receipt line only once.");
            }
            LineRefund refund = saleSupport.refundFor(item, qty);
            total = total.add(refund.amount());
            tax = tax.add(refund.tax());
            result.add(new ReturnItemDto(item.getId(), item.getProductId(), item.getProductNameSnapshot(), qty,
                    refund.amount(), refund.tax(), line.condition(), line.restock() && line.condition() == ReturnItem.Condition.SALEABLE));
        }
        if (total.signum() <= 0) {
            throw ApiException.rule("Nothing to refund.");
        }
        return new Computed(MoneyPolicy.amount(total), MoneyPolicy.amount(tax), result);
    }

    private List<TenderAllowance> allowances(Sale sale) {
        Map<String, BigDecimal> refunded = new HashMap<>();
        for (Object[] row : returns.refundedByTender(sale.getId())) {
            refunded.put((String) row[0], (BigDecimal) row[1]);
        }
        Map<String, BigDecimal> paid = new LinkedHashMap<>();
        for (PaymentDto p : payments.forSale(sale.getId())) {
            paid.merge(p.tenderType(), p.amount(), BigDecimal::add);
        }
        return paid.entrySet().stream().map(e -> {
            BigDecimal back = refunded.getOrDefault(e.getKey(), BigDecimal.ZERO);
            return new TenderAllowance(e.getKey(), e.getValue(), MoneyPolicy.amount(back),
                    MoneyPolicy.amount(e.getValue().subtract(back).max(BigDecimal.ZERO)));
        }).toList();
    }

    private boolean withinWindow(Sale sale) {
        int days = settings.integer(sale.getStoreId(), "returns.windowDays", 14);
        return sale.getCompletedAt().plus(Duration.ofDays(days)).isAfter(clock.instant());
    }

    private static long pointsToReverse(Sale sale, BigDecimal refund) {
        if (sale.getLoyaltyPointsEarned() <= 0 || sale.getGrandTotal().signum() == 0) {
            return 0;
        }
        return BigDecimal.valueOf(sale.getLoyaltyPointsEarned()).multiply(refund)
                .divide(sale.getGrandTotal(), 0, RoundingMode.FLOOR).longValue();
    }

    private ReturnDto load(Long id) {
        SaleReturn r = returns.findWithItemsById(id).orElseThrow(() -> ApiException.notFound("Return", id));
        return toDto(r, saleQueries.loadDto(r.getOriginalSaleId()).receiptNumber());
    }

    private ReturnDto toDto(SaleReturn r, String receipt) {
        Map<Long, String> names = users.displayNames(r.getApprovedBy() == null ? List.of(r.getProcessedBy())
                : List.of(r.getProcessedBy(), r.getApprovedBy()));
        Map<Long, Product> productMap = products.loadForPricing(r.getItems().stream().map(ReturnItem::getProductId).toList());
        List<ReturnItemDto> items = r.getItems().stream().map(i -> new ReturnItemDto(i.getSaleItemId(), i.getProductId(),
                productMap.containsKey(i.getProductId()) ? productMap.get(i.getProductId()).getName() : null, i.getQuantity(),
                i.getRefundAmount(), i.getTaxAmount(), i.getItemCondition(), i.isRestock())).toList();
        List<RefundDto> refundDtos = refunds.findByReturnIdOrderByIdAsc(r.getId()).stream()
                .map(f -> new RefundDto(f.getTenderType(), f.getAmount(), f.getReference(), f.getCardLast4(), f.getLoyaltyPoints()))
                .toList();
        return new ReturnDto(r.getId(), r.getReturnNumber(), r.getOriginalSaleId(), receipt, r.getReasonCode(), r.getNotes(),
                r.getRefundTotal(), r.getTaxTotal(), items, refundDtos, names.get(r.getProcessedBy()),
                r.getApprovedBy() == null ? null : names.get(r.getApprovedBy()), r.getLoyaltyPointsReversed(), r.getCreatedAt());
    }

    private CurrentUser requireReturnPermission() {
        CurrentUser user = currentUser.require();
        if (!user.has("RETURN_CREATE")) {
            throw ApiException.forbidden("Processing returns requires RETURN_CREATE.");
        }
        return user;
    }
}
