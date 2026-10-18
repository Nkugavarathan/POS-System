package com.supermarket.pos.sales.service;

import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.auth.entity.ApprovalAction;
import com.supermarket.pos.auth.service.ApprovalService;
import com.supermarket.pos.auth.service.ApprovalService.ConsumedApproval;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.idempotency.IdempotencyRecord;
import com.supermarket.pos.common.idempotency.IdempotencyService;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.common.security.CurrentUser;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.common.sequence.SequenceService;
import com.supermarket.pos.customers.entity.Customer;
import com.supermarket.pos.customers.entity.LoyaltyAccount;
import com.supermarket.pos.customers.entity.LoyaltyTransaction;
import com.supermarket.pos.customers.service.CustomerService;
import com.supermarket.pos.customers.service.LoyaltyService;
import com.supermarket.pos.inventory.entity.MovementType;
import com.supermarket.pos.inventory.service.InventoryService;
import com.supermarket.pos.inventory.service.InventoryService.Allocation;
import com.supermarket.pos.inventory.service.InventoryService.Deduction;
import com.supermarket.pos.inventory.service.InventoryService.Reference;
import com.supermarket.pos.inventory.service.InventoryService.Shortage;
import com.supermarket.pos.payments.service.PaymentProvider.PaymentContext;
import com.supermarket.pos.payments.service.PaymentProvider.PlannedPayment;
import com.supermarket.pos.payments.service.PaymentService;
import com.supermarket.pos.pos.entity.CashierShift;
import com.supermarket.pos.pos.service.ShiftService;
import com.supermarket.pos.products.entity.Product;
import com.supermarket.pos.sales.dto.SalesDtos.CartLine;
import com.supermarket.pos.sales.dto.SalesDtos.CartRequest;
import com.supermarket.pos.sales.dto.SalesDtos.CheckoutRequest;
import com.supermarket.pos.sales.dto.SalesDtos.DiscountRequest;
import com.supermarket.pos.sales.dto.SalesDtos.LineDto;
import com.supermarket.pos.sales.dto.SalesDtos.LoyaltyPreview;
import com.supermarket.pos.sales.dto.SalesDtos.QuoteResponse;
import com.supermarket.pos.sales.dto.SalesDtos.RequiredApproval;
import com.supermarket.pos.sales.dto.SalesDtos.SaleDto;
import com.supermarket.pos.sales.dto.SalesDtos.TotalsDto;
import com.supermarket.pos.sales.dto.SalesDtos.Warning;
import com.supermarket.pos.sales.entity.Sale;
import com.supermarket.pos.sales.entity.SaleDiscount;
import com.supermarket.pos.sales.entity.SaleItem;
import com.supermarket.pos.sales.event.SaleCompleted;
import com.supermarket.pos.sales.repository.SaleDiscountRepository;
import com.supermarket.pos.sales.repository.SaleRepository;
import com.supermarket.pos.sales.service.PricingEngine.LineInput;
import com.supermarket.pos.sales.service.PricingEngine.PricedCart;
import com.supermarket.pos.sales.service.PricingEngine.PricedLine;
import com.supermarket.pos.store.entity.Store;
import com.supermarket.pos.store.service.StoreService;
import com.supermarket.pos.users.service.UserAccountService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Quote (advisory, no side effects) and checkout (exactly-once, all-or-nothing). See
 * docs/02-business-workflows.md W4 for the step list this implements.
 */
@Service
public class CheckoutService {

    public static final String OPERATION = "CHECKOUT";
    public static final String REFERENCE_TYPE = "SALE";

    private final PricingEngine pricing;
    private final ShiftService shifts;
    private final InventoryService inventory;
    private final PaymentService payments;
    private final CustomerService customers;
    private final LoyaltyService loyalty;
    private final ApprovalService approvals;
    private final StoreService stores;
    private final UserAccountService users;
    private final SequenceService sequences;
    private final IdempotencyService idempotency;
    private final SaleRepository sales;
    private final SaleDiscountRepository discounts;
    private final SaleQueryService queries;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate tx;

    public CheckoutService(PricingEngine pricing, ShiftService shifts, InventoryService inventory, PaymentService payments,
                           CustomerService customers, LoyaltyService loyalty, ApprovalService approvals,
                           StoreService stores, UserAccountService users, SequenceService sequences,
                           IdempotencyService idempotency, SaleRepository sales, SaleDiscountRepository discounts,
                           SaleQueryService queries, CurrentUserProvider currentUser, AuditService audit,
                           ApplicationEventPublisher events, Clock clock, PlatformTransactionManager txManager) {
        this.pricing = pricing;
        this.shifts = shifts;
        this.inventory = inventory;
        this.payments = payments;
        this.customers = customers;
        this.loyalty = loyalty;
        this.approvals = approvals;
        this.stores = stores;
        this.users = users;
        this.sequences = sequences;
        this.idempotency = idempotency;
        this.sales = sales;
        this.discounts = discounts;
        this.queries = queries;
        this.currentUser = currentUser;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    // ----- quote -------------------------------------------------------------------------------

    @PreAuthorize("hasAuthority('POS_SELL')")
    @Transactional(readOnly = true)
    public QuoteResponse quote(CartRequest request) {
        CurrentUser user = currentUser.require();
        Long storeId = user.requireActiveStore();
        Store store = stores.requireStore(storeId);
        PricedCart cart = pricing.price(inputs(request.items()), request.transactionDiscount(), store.isPricesIncludeTax());

        List<RequiredApproval> required = requiredApprovals(cart, user).stream()
                .map(r -> new RequiredApproval(r.action().name(), r.value(), r.reason(),
                        approvals.findMatching(request.approvalTokens(), r.action()).isPresent()))
                .toList();

        Map<Long, BigDecimal> requested = new LinkedHashMap<>();
        cart.lines().forEach(l -> requested.merge(l.product().getId(), l.quantity(), BigDecimal::add));
        Map<Long, Product> productMap = cart.lines().stream()
                .collect(Collectors.toMap(l -> l.product().getId(), PricedLine::product, (a, b) -> a));
        Map<Long, Shortage> shortages = inventory.shortages(storeId, requested, productMap);
        List<Warning> warnings = new ArrayList<>();
        shortages.values().forEach(s -> warnings.add(new Warning("INSUFFICIENT_STOCK", s.productId(),
                "Only " + s.available().stripTrailingZeros().toPlainString() + " of " + s.name() + " in stock.")));

        LoyaltyPreview preview = null;
        if (request.customerId() != null) {
            Customer customer = customers.require(request.customerId());
            var rules = loyalty.rules(storeId);
            long balance = loyalty.find(customer.getId()).map(LoyaltyAccount::getPointsBalance).orElse(0L);
            preview = new LoyaltyPreview(customer.getId(), customer.getFullName(), balance,
                    LoyaltyService.valueOf(rules, balance),
                    loyalty.find(customer.getId()).isPresent() ? LoyaltyService.pointsFor(rules, cart.grandTotal()) : 0);
        }
        List<LineDto> lines = cart.lines().stream().map(l -> toLine(l, shortages)).toList();
        return new QuoteResponse(store.getCurrency(), cart.pricesIncludeTax(), lines, totals(cart), required, warnings, preview);
    }

    // ----- checkout ----------------------------------------------------------------------------

    public IdempotencyService.Outcome<SaleDto> checkout(CheckoutRequest request, String idempotencyKey) {
        CurrentUser user = currentUser.require();
        if (!user.has("POS_SELL")) {
            throw ApiException.forbidden("Selling requires POS_SELL.");
        }
        IdempotencyService.Request idem = idempotency.request(user.id(), OPERATION, idempotencyKey, request);
        return idempotency.execute(idem, () -> tx.execute(status -> doCheckout(request, idem, user)),
                queries::loadDto);
    }

    private SaleDto doCheckout(CheckoutRequest request, IdempotencyService.Request idem, CurrentUser user) {
        IdempotencyRecord record = idempotency.register(idem);
        Long storeId = user.requireActiveStore();
        CashierShift shift = shifts.lockOpenShift(user.id(), storeId, request.registerId());
        Store store = stores.requireStore(storeId);
        PricedCart cart = pricing.price(inputs(request.items()), request.transactionDiscount(), store.isPricesIncludeTax());

        // Approvals are consumed inside this transaction: if anything later fails, they stay unused.
        Map<ApprovalAction, ConsumedApproval> consumed = new LinkedHashMap<>();
        for (Required r : requiredApprovals(cart, user)) {
            String token = approvals.findMatching(request.approvalTokens(), r.action())
                    .orElseThrow(() -> new ApiException(ErrorCode.APPROVAL_REQUIRED, r.reason(),
                            Map.of("action", r.action().name(), "value", r.value() == null ? "" : r.value().toPlainString())));
            consumed.put(r.action(), approvals.consume(token, r.action(), storeId, user.id(), r.value()));
        }

        if (cart.grandTotal().compareTo(MoneyPolicy.amount(request.expectedGrandTotal())) != 0) {
            throw new ApiException(ErrorCode.QUOTE_MISMATCH, "The total changed to " + cart.grandTotal()
                    + " (prices or promotions were updated). Review and confirm the new total.",
                    Map.of("grandTotal", cart.grandTotal().toPlainString()));
        }

        Customer customer = request.customerId() == null ? null : customers.require(request.customerId());
        if (customer != null && !customer.isActive()) {
            throw ApiException.rule("This customer is inactive.");
        }
        var rules = loyalty.rules(storeId);
        Optional<LoyaltyAccount> account = customer == null ? Optional.empty() : loyalty.find(customer.getId());
        List<PlannedPayment> planned = payments.plan(cart.grandTotal(), request.payments(), new PaymentContext(storeId,
                customer == null ? null : customer.getId(), account.map(LoyaltyAccount::getPointsBalance).orElse(0L),
                rules.pointValue(), rules.minRedeemPoints()));

        Instant now = clock.instant();
        String registerCode = stores.registerCodes(List.of(shift.getRegisterId())).get(shift.getRegisterId());
        Sale sale = buildSale(cart, store, shift, user, customer, planned, now);
        sale.setReceiptNumber(sequences.nextDailyNumber("RECEIPT", store.getCode() + "-" + registerCode,
                stores.businessDate(store, now), 6));
        Map<Integer, SaleItem> itemsByLine = new LinkedHashMap<>();
        for (PricedLine line : cart.lines()) {
            SaleItem item = toItem(line, sale, consumed.get(ApprovalAction.PRICE_OVERRIDE), user);
            sale.getItems().add(item);
            itemsByLine.put(line.lineNumber(), item);
        }
        Sale saved = sales.saveAndFlush(sale);
        saveDiscounts(saved, cart, itemsByLine, request.transactionDiscount(), consumed, user);
        payments.record(saved.getId(), planned, user.id());

        // Stock leaves the ledger in the same transaction (row locks, no overselling).
        List<Deduction> deductions = cart.lines().stream()
                .map(l -> new Deduction(l.product().getId(), l.quantity(), itemsByLine.get(l.lineNumber()).getId()))
                .toList();
        List<Allocation> allocations = inventory.deduct(storeId, MovementType.SALE,
                new Reference(REFERENCE_TYPE, saved.getId(), user.id(), saved.getReceiptNumber()), deductions);
        BigDecimal costTotal = BigDecimal.ZERO;
        for (Allocation a : allocations) {
            SaleItem item = saved.getItems().stream().filter(i -> i.getId().equals(a.lineId())).findFirst().orElseThrow();
            item.setUnitCost(a.unitCost());
            item.setCostAmount(a.costAmount());
            costTotal = costTotal.add(a.costAmount());
        }
        saved.setCostTotal(MoneyPolicy.amount(costTotal));

        applyLoyalty(saved, planned, account, rules, user);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("grandTotal", saved.getGrandTotal().toPlainString());
        details.put("lines", saved.getItems().size());
        details.put("tenders", planned.stream().map(PlannedPayment::tenderType).toList());
        if (!consumed.isEmpty()) {
            details.put("approvals", consumed.values().stream()
                    .map(c -> Map.of("action", c.action().name(), "approvedBy", c.approverId())).toList());
        }
        if (allocations.stream().anyMatch(Allocation::usedExpiredBatch)) {
            details.put("warning", "Stock was taken from an expired batch");
        }
        audit.record(AuditService.Entry.success("SALE_COMPLETED", "Sale", saved.getId(), saved.getReceiptNumber())
                .withStore(storeId).withDetails(details));
        consumed.values().forEach(c -> approvals.linkReference(c.grantId(), REFERENCE_TYPE, saved.getId()));
        idempotency.complete(record, "SALE", saved.getId(), 201);
        events.publishEvent(new SaleCompleted(saved.getId(), storeId, saved.getGrandTotal()));
        return queries.toDto(saved, false);
    }

    // ----- helpers -----------------------------------------------------------------------------

    private record Required(ApprovalAction action, BigDecimal value, String reason) {
    }

    private List<Required> requiredApprovals(PricedCart cart, CurrentUser user) {
        List<Required> required = new ArrayList<>();
        if (cart.hasManualDiscount()) {
            BigDecimal limit = user.has("DISCOUNT_APPLY") ? users.maxDiscountPercent(user.id()) : BigDecimal.ZERO;
            if (!user.has("DISCOUNT_APPLY") || cart.maxManualDiscountPercent().compareTo(limit) > 0) {
                required.add(new Required(ApprovalAction.DISCOUNT_OVER_LIMIT, cart.maxManualDiscountPercent(),
                        "A " + cart.maxManualDiscountPercent().stripTrailingZeros().toPlainString()
                                + "% discount is above your limit of " + limit.stripTrailingZeros().toPlainString() + "%."));
            }
        }
        if (cart.hasPriceOverride() && !user.has("PRICE_OVERRIDE")) {
            required.add(new Required(ApprovalAction.PRICE_OVERRIDE, null, "Price overrides need manager approval."));
        }
        return required;
    }

    static List<LineInput> inputs(List<CartLine> lines) {
        return lines.stream().map(l -> new LineInput(l.productId(), l.quantity(), l.manualDiscount(),
                l.priceOverride() == null ? null : l.priceOverride().unitPrice())).toList();
    }

    private static Sale buildSale(PricedCart cart, Store store, CashierShift shift, CurrentUser user, Customer customer,
                                  List<PlannedPayment> planned, Instant now) {
        Sale sale = new Sale();
        sale.setStoreId(store.getId());
        sale.setRegisterId(shift.getRegisterId());
        sale.setShiftId(shift.getId());
        sale.setCashierId(user.id());
        sale.setCustomerId(customer == null ? null : customer.getId());
        sale.setCurrency(store.getCurrency());
        sale.setPricesIncludeTax(cart.pricesIncludeTax());
        sale.setSubtotal(cart.subtotal());
        sale.setLineDiscountTotal(cart.lineDiscountTotal());
        sale.setTransactionDiscountTotal(cart.transactionDiscountTotal());
        sale.setDiscountTotal(cart.discountTotal());
        sale.setTaxTotal(cart.taxTotal());
        sale.setGrandTotal(cart.grandTotal());
        sale.setAmountPaid(cart.grandTotal());
        BigDecimal tendered = planned.stream().map(PlannedPayment::amountTendered).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal change = planned.stream().map(PlannedPayment::changeGiven).reduce(BigDecimal.ZERO, BigDecimal::add);
        sale.setAmountTendered(MoneyPolicy.amount(tendered));
        sale.setChangeDue(MoneyPolicy.amount(change));
        sale.setItemCount(cart.itemCount());
        sale.setCostTotal(MoneyPolicy.ZERO);
        sale.setCompletedAt(now);
        return sale;
    }

    private static SaleItem toItem(PricedLine l, Sale sale, ConsumedApproval overrideApproval, CurrentUser user) {
        SaleItem i = new SaleItem();
        i.setSale(sale);
        i.setLineNumber(l.lineNumber());
        i.setProductId(l.product().getId());
        i.setSkuSnapshot(l.product().getSku());
        i.setBarcodeSnapshot(l.barcode());
        i.setProductNameSnapshot(l.product().getName());
        i.setCategoryId(l.product().getCategory().getId());
        i.setUnitOfMeasure(l.product().getUnitOfMeasure().name());
        i.setQuantity(l.quantity());
        i.setUnitPrice(l.unitPrice());
        i.setOriginalUnitPrice(l.originalUnitPrice());
        if (l.overridden()) {
            i.setPriceOverrideBy(overrideApproval != null ? overrideApproval.approverId() : user.id());
        }
        i.setGrossAmount(l.gross());
        i.setPromoDiscountAmount(l.promo());
        i.setManualDiscountAmount(l.manual());
        i.setAllocatedTxnDiscountAmount(l.allocated());
        i.setNetAmount(l.net());
        i.setTaxRatePercent(l.taxRate());
        i.setTaxAmount(l.tax());
        i.setLineTotal(l.lineTotal());
        i.setUnitCost(BigDecimal.ZERO);
        i.setCostAmount(MoneyPolicy.ZERO);
        return i;
    }

    private void saveDiscounts(Sale sale, PricedCart cart, Map<Integer, SaleItem> items, DiscountRequest txn,
                               Map<ApprovalAction, ConsumedApproval> consumed, CurrentUser user) {
        Long discountApprover = consumed.containsKey(ApprovalAction.DISCOUNT_OVER_LIMIT)
                ? consumed.get(ApprovalAction.DISCOUNT_OVER_LIMIT).approverId() : null;
        List<SaleDiscount> rows = new ArrayList<>();
        for (PricedLine l : cart.lines()) {
            Long itemId = items.get(l.lineNumber()).getId();
            if (l.promo().signum() > 0) {
                rows.add(discount(sale, itemId, SaleDiscount.Source.PROMOTION, l.promotion().getDiscountType().name(),
                        l.promotion().getDiscountValue(), l.promo(), l.promotion().getName(), l.promotion().getId(), user, null));
            }
            if (l.manual().signum() > 0) {
                rows.add(discount(sale, itemId, SaleDiscount.Source.MANUAL_LINE, l.manualRequest().method(),
                        l.manualRequest().value(), l.manual(), l.manualRequest().reason(), null, user, discountApprover));
            }
            if (l.overridden()) {
                rows.add(discount(sale, itemId, SaleDiscount.Source.PRICE_OVERRIDE, "AMOUNT",
                        l.originalUnitPrice().subtract(l.unitPrice()), MoneyPolicy.extend(l.originalUnitPrice(), l.quantity())
                                .subtract(l.gross()), null, null, user,
                        consumed.containsKey(ApprovalAction.PRICE_OVERRIDE)
                                ? consumed.get(ApprovalAction.PRICE_OVERRIDE).approverId() : null));
            }
        }
        if (cart.transactionDiscountTotal().signum() > 0) {
            rows.add(discount(sale, null, SaleDiscount.Source.MANUAL_TRANSACTION, txn.method(), txn.value(),
                    cart.transactionDiscountTotal(), txn.reason(), null, user, discountApprover));
        }
        discounts.saveAll(rows);
    }

    private static SaleDiscount discount(Sale sale, Long itemId, SaleDiscount.Source source, String method, BigDecimal value,
                                         BigDecimal amount, String reason, Long promotionId, CurrentUser user, Long approver) {
        SaleDiscount d = new SaleDiscount();
        d.setSaleId(sale.getId());
        d.setSaleItemId(itemId);
        d.setSource(source);
        d.setMethod(method);
        d.setValue(MoneyPolicy.amount(value));
        d.setAmount(MoneyPolicy.amount(amount));
        d.setReason(reason);
        d.setPromotionId(promotionId);
        d.setAppliedBy(user.id());
        d.setApprovedBy(approver);
        return d;
    }

    private void applyLoyalty(Sale sale, List<PlannedPayment> planned, Optional<LoyaltyAccount> account,
                              LoyaltyService.Rules rules, CurrentUser user) {
        if (account.isEmpty() || sale.getCustomerId() == null) {
            return;
        }
        long redeemed = planned.stream().filter(p -> p.loyaltyPoints() != null).mapToLong(PlannedPayment::loyaltyPoints).sum();
        BigDecimal paidByPoints = planned.stream().filter(p -> "LOYALTY".equals(p.tenderType()))
                .map(PlannedPayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (redeemed > 0) {
            loyalty.post(sale.getCustomerId(), LoyaltyTransaction.Type.REDEEM, -redeemed, REFERENCE_TYPE, sale.getId(),
                    "Paid " + paidByPoints + " on " + sale.getReceiptNumber(), user.id());
            sale.setLoyaltyPointsRedeemed(redeemed);
        }
        long earned = LoyaltyService.pointsFor(rules, sale.getGrandTotal().subtract(paidByPoints));
        if (earned > 0) {
            loyalty.post(sale.getCustomerId(), LoyaltyTransaction.Type.EARN, earned, REFERENCE_TYPE, sale.getId(),
                    sale.getReceiptNumber(), user.id());
            sale.setLoyaltyPointsEarned(earned);
        }
    }

    static TotalsDto totals(PricedCart c) {
        return new TotalsDto(c.subtotal(), c.lineDiscountTotal(), c.transactionDiscountTotal(), c.discountTotal(),
                c.taxTotal(), c.grandTotal(), c.itemCount());
    }

    private static LineDto toLine(PricedLine l, Map<Long, Shortage> shortages) {
        Shortage s = shortages.get(l.product().getId());
        return new LineDto(l.lineNumber(), l.product().getId(), l.product().getName(), l.product().getSku(), l.barcode(),
                l.product().getUnitOfMeasure().name(), l.quantity(), l.unitPrice(), l.originalUnitPrice(), l.gross(),
                l.promo(), l.promotion() == null ? null : l.promotion().getName(), l.manual(), l.allocated(), l.net(),
                l.taxRate(), l.tax(), l.lineTotal(), s == null ? null : s.available(), s == null, null);
    }
}
