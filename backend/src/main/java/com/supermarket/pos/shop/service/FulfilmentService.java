package com.supermarket.pos.shop.service;

import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.idempotency.IdempotencyRecord;
import com.supermarket.pos.common.idempotency.IdempotencyService;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.common.security.CurrentUser;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.common.web.PageRequests;
import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.inventory.entity.MovementType;
import com.supermarket.pos.inventory.service.InventoryService;
import com.supermarket.pos.inventory.service.InventoryService.Allocation;
import com.supermarket.pos.inventory.service.InventoryService.Deduction;
import com.supermarket.pos.inventory.service.InventoryService.Receipt;
import com.supermarket.pos.inventory.service.InventoryService.Reference;
import com.supermarket.pos.products.entity.Product;
import com.supermarket.pos.products.service.ProductService;
import com.supermarket.pos.products.service.QuantityRules;
import com.supermarket.pos.sales.dto.SalesDtos.SaleDto;
import com.supermarket.pos.sales.service.PricingEngine;
import com.supermarket.pos.sales.service.PricingEngine.LineInput;
import com.supermarket.pos.sales.service.PrepricedSaleService;
import com.supermarket.pos.sales.service.PrepricedSaleService.PrepricedLine;
import com.supermarket.pos.sales.service.PrepricedSaleService.PrepricedSale;
import com.supermarket.pos.sales.service.SaleQueryService;
import com.supermarket.pos.shop.dto.ShopDtos.BoardSummaryDto;
import com.supermarket.pos.shop.dto.ShopDtos.CollectRequest;
import com.supermarket.pos.shop.dto.ShopDtos.OrderDto;
import com.supermarket.pos.shop.dto.ShopDtos.OrderSummaryDto;
import com.supermarket.pos.shop.entity.OnlineOrder;
import com.supermarket.pos.shop.entity.OnlineOrderItem;
import com.supermarket.pos.shop.repository.OnlineOrderRepository;
import com.supermarket.pos.store.entity.Store;
import com.supermarket.pos.store.service.SettingsService;
import com.supermarket.pos.store.service.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Store side of click &amp; collect. Packing is item by item (the customer watches progress), out-of-stock
 * items get a substitute proposal or are dropped, and "ready" is the moment stock leaves the ledger.
 * Collecting at the till turns the order into a normal sale at the packed prices.
 */
@Service
public class FulfilmentService implements SaleQueryService.OnlineOrderNumbers {

    private static final Logger log = LoggerFactory.getLogger(FulfilmentService.class);
    public static final String REFERENCE_TYPE = "ONLINE_ORDER";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a");

    private final OnlineOrderRepository orders;
    private final OrderViews views;
    private final NotificationService notifications;
    private final InventoryService inventory;
    private final ProductService products;
    private final PricingEngine pricing;
    private final PrepricedSaleService prepricedSales;
    private final StoreService stores;
    private final SettingsService settings;
    private final IdempotencyService idempotency;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;
    private final SaleQueryService saleQueries;
    private final Clock clock;
    private final TransactionTemplate tx;

    public FulfilmentService(OnlineOrderRepository orders, OrderViews views, NotificationService notifications,
                             InventoryService inventory, ProductService products, PricingEngine pricing,
                             PrepricedSaleService prepricedSales, StoreService stores, SettingsService settings,
                             IdempotencyService idempotency, CurrentUserProvider currentUser, AuditService audit,
                             SaleQueryService saleQueries, Clock clock, PlatformTransactionManager txManager) {
        this.orders = orders;
        this.views = views;
        this.notifications = notifications;
        this.inventory = inventory;
        this.products = products;
        this.pricing = pricing;
        this.prepricedSales = prepricedSales;
        this.stores = stores;
        this.settings = settings;
        this.idempotency = idempotency;
        this.currentUser = currentUser;
        this.audit = audit;
        this.saleQueries = saleQueries;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    // ----- board -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryDto> board(Set<OnlineOrder.Status> statuses, String q, Integer page, Integer size) {
        CurrentUser user = staff("ONLINE_ORDER_MANAGE", "POS_SELL");
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.by(Sort.Direction.ASC, "placedAt"));
        Set<OnlineOrder.Status> filter = statuses == null || statuses.isEmpty() ? EnumSet.of(OnlineOrder.Status.PLACED) : statuses;
        return PageResponse.of(orders.search(user.requireActiveStore(), false, filter,
                q == null || q.isBlank() ? null : q.trim(), pageable), o -> views.summary(o, true));
    }

    @Transactional(readOnly = true)
    public BoardSummaryDto summary() {
        CurrentUser user = staff("ONLINE_ORDER_MANAGE", "POS_SELL");
        Long storeId = user.requireActiveStore();
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : orders.openCounts(storeId, EnumSet.of(OnlineOrder.Status.COLLECTED,
                OnlineOrder.Status.CANCELLED, OnlineOrder.Status.EXPIRED))) {
            counts.put(((OnlineOrder.Status) row[0]).name(), (Long) row[1]);
        }
        return new BoardSummaryDto(counts, orders.countArrived(storeId, OnlineOrder.Status.READY_FOR_PICKUP));
    }

    @Transactional(readOnly = true)
    public OrderDto get(Long id) {
        staff("ONLINE_ORDER_MANAGE", "POS_SELL");
        return views.forStaff(load(id));
    }

    /** POS: find a ready order by its 6-digit pickup code (or order number). */
    @Transactional(readOnly = true)
    public OrderDto findForCollection(String codeOrNumber) {
        CurrentUser user = staff("POS_SELL");
        String value = codeOrNumber.trim();
        OnlineOrder order = value.matches("\\d{6}")
                ? orders.findFirstByStoreIdAndPickupCodeAndStatus(user.requireActiveStore(), value, OnlineOrder.Status.READY_FOR_PICKUP)
                .orElseThrow(() -> ApiException.notFound("Ready order", value))
                : orders.findWithItemsByOrderNumber(value).orElseThrow(() -> ApiException.notFound("Order", value));
        user.assertStoreAccess(order.getStoreId(), "Order", value);
        return views.forStaff(orders.findWithItemsById(order.getId()).orElseThrow());
    }

    // ----- workflow ------------------------------------------------------------------------------

    @Transactional
    public OrderDto confirm(Long id) {
        CurrentUser user = staff("ONLINE_ORDER_MANAGE");
        OnlineOrder o = lock(id, user);
        requireStatus(o, OnlineOrder.Status.PLACED);
        o.setStatus(OnlineOrder.Status.CONFIRMED);
        o.setConfirmedAt(clock.instant());
        o.setHandledBy(user.id());
        views.event(o, "Order confirmed by the store", "STAFF", user.id());
        notifications.notify(o.getCustomerAccountId(), o.getId(), "ORDER", "Order " + o.getOrderNumber() + " confirmed",
                "The store has your order and will start packing shortly.");
        return views.forStaff(o);
    }

    @Transactional
    public OrderDto startPacking(Long id) {
        CurrentUser user = staff("ONLINE_ORDER_MANAGE");
        OnlineOrder o = lock(id, user);
        if (o.getStatus() != OnlineOrder.Status.PLACED && o.getStatus() != OnlineOrder.Status.CONFIRMED) {
            throw ApiException.invalidState("Packing can start only for placed or confirmed orders.");
        }
        Instant now = clock.instant();
        if (o.getConfirmedAt() == null) {
            o.setConfirmedAt(now);
        }
        o.setStatus(OnlineOrder.Status.PACKING);
        o.setPackingStartedAt(now);
        o.setHandledBy(user.id());
        views.event(o, "Packing started", "STAFF", user.id());
        notifications.notify(o.getCustomerAccountId(), o.getId(), "ORDER", "We started packing " + o.getOrderNumber(),
                "Follow each item being packed in your order page.");
        return views.forStaff(o);
    }

    /** Item packed. Weighed items may come in slightly lighter; never more than ordered (you never pay more). */
    @Transactional
    public OrderDto pick(Long id, Long itemId, BigDecimal pickedQuantity) {
        CurrentUser user = staff("ONLINE_ORDER_MANAGE");
        OnlineOrder o = lock(id, user);
        requireStatus(o, OnlineOrder.Status.PACKING);
        OnlineOrderItem item = item(o, itemId);
        BigDecimal qty = pickedQuantity == null ? item.getQuantity() : MoneyPolicy.quantity(pickedQuantity);
        Product product = products.require(item.getProductId());
        QuantityRules.validate(product, qty);
        if (qty.compareTo(item.getQuantity()) > 0) {
            throw ApiException.rule("Pack at most the ordered quantity (" + item.getQuantity().stripTrailingZeros().toPlainString() + ").");
        }
        item.setPickStatus(OnlineOrderItem.PickStatus.PICKED);
        item.setPickedQuantity(qty);
        clearSubstitute(item);
        views.event(o, "Packed " + item.getProductNameSnapshot(), "STAFF", user.id());
        return views.forStaff(o);
    }

    /** Out of stock: offer a replacement (only if the customer allowed substitutes). */
    @Transactional
    public OrderDto proposeSubstitute(Long id, Long itemId, Long productId, BigDecimal quantity) {
        CurrentUser user = staff("ONLINE_ORDER_MANAGE");
        OnlineOrder o = lock(id, user);
        requireStatus(o, OnlineOrder.Status.PACKING);
        OnlineOrderItem item = item(o, itemId);
        if (item.getSubstitutionPreference() == OnlineOrderItem.Preference.SKIP) {
            throw ApiException.rule("The customer asked not to substitute this item. Mark it unavailable instead.");
        }
        if (productId.equals(item.getProductId())) {
            throw ApiException.rule("Choose a different product as the substitute.");
        }
        Product sub = products.require(productId);
        BigDecimal qty = QuantityRules.validate(sub, quantity);
        var priced = pricing.price(o.getStoreId(), List.of(new LineInput(sub.getId(), qty, null, null)), null,
                o.isPricesIncludeTax()).lines().get(0);
        // The customer never pays more than for the item they ordered.
        BigDecimal lineTotal = priced.lineTotal().min(item.getLineTotal());
        item.setSubstituteProductId(sub.getId());
        item.setSubstituteName(sub.getName());
        item.setSubstituteQuantity(qty);
        item.setSubstituteLineTotal(lineTotal);
        item.setSubstituteTaxRate(sub.getTaxRate().getRatePercent());
        item.setPickStatus(OnlineOrderItem.PickStatus.SUBSTITUTE_PROPOSED);
        views.event(o, "Substitute proposed for " + item.getProductNameSnapshot() + ": " + sub.getName(), "STAFF", user.id());
        notifications.notify(o.getCustomerAccountId(), o.getId(), "SUBSTITUTION", "Substitute for " + item.getProductNameSnapshot() + "?",
                item.getProductNameSnapshot() + " is out of stock. We can give you " + sub.getName() + " for "
                        + lineTotal.toPlainString() + " instead. Open your order to accept or decline.");
        return views.forStaff(o);
    }

    @Transactional
    public OrderDto markUnavailable(Long id, Long itemId) {
        CurrentUser user = staff("ONLINE_ORDER_MANAGE");
        OnlineOrder o = lock(id, user);
        requireStatus(o, OnlineOrder.Status.PACKING);
        OnlineOrderItem item = item(o, itemId);
        item.setPickStatus(OnlineOrderItem.PickStatus.UNAVAILABLE);
        item.setPickedQuantity(null);
        clearSubstitute(item);
        views.event(o, item.getProductNameSnapshot() + " is unavailable and removed from your bill", "STAFF", user.id());
        return views.forStaff(o);
    }

    @Transactional
    public OrderDto resetItem(Long id, Long itemId) {
        CurrentUser user = staff("ONLINE_ORDER_MANAGE");
        OnlineOrder o = lock(id, user);
        requireStatus(o, OnlineOrder.Status.PACKING);
        OnlineOrderItem item = item(o, itemId);
        item.setPickStatus(OnlineOrderItem.PickStatus.PENDING);
        item.setPickedQuantity(null);
        clearSubstitute(item);
        return views.forStaff(o);
    }

    /**
     * Packing done: every line resolved. The packed goods leave the stock ledger now (they are off
     * the shelf), the final bill is computed and the customer gets the pickup code.
     */
    @Transactional
    public OrderDto markReady(Long id) {
        CurrentUser user = staff("ONLINE_ORDER_MANAGE");
        OnlineOrder o = lock(id, user);
        requireStatus(o, OnlineOrder.Status.PACKING);
        if (o.getItems().stream().anyMatch(i -> i.getPickStatus() == OnlineOrderItem.PickStatus.SUBSTITUTE_PROPOSED)) {
            throw ApiException.rule("Waiting for the customer to answer a substitution. Mark the item unavailable to continue.");
        }
        if (o.getItems().stream().anyMatch(i -> !i.isResolved())) {
            throw ApiException.rule("Pack, substitute or mark unavailable every item first.");
        }
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        List<Deduction> picks = new ArrayList<>();
        for (OnlineOrderItem i : o.getItems()) {
            switch (i.getPickStatus()) {
                case PICKED -> {
                    boolean full = i.getPickedQuantity().compareTo(i.getQuantity()) == 0;
                    i.setFinalProductId(i.getProductId());
                    i.setFinalQuantity(i.getPickedQuantity());
                    i.setFinalLineTotal(full ? i.getLineTotal() : scale(i.getLineTotal(), i.getPickedQuantity(), i.getQuantity()));
                    i.setFinalTaxAmount(full ? i.getTaxAmount() : scale(i.getTaxAmount(), i.getPickedQuantity(), i.getQuantity()));
                }
                case SUBSTITUTED -> {
                    i.setFinalProductId(i.getSubstituteProductId());
                    i.setFinalQuantity(i.getSubstituteQuantity());
                    i.setFinalLineTotal(i.getSubstituteLineTotal());
                    i.setFinalTaxAmount(MoneyPolicy.taxIncluded(i.getSubstituteLineTotal(), i.getSubstituteTaxRate()));
                }
                default -> {
                    i.setFinalProductId(null);
                    i.setFinalQuantity(BigDecimal.ZERO);
                    i.setFinalLineTotal(MoneyPolicy.ZERO);
                    i.setFinalTaxAmount(MoneyPolicy.ZERO);
                }
            }
            total = total.add(i.getFinalLineTotal());
            tax = tax.add(i.getFinalTaxAmount());
            if (i.getFinalProductId() != null) {
                picks.add(new Deduction(i.getFinalProductId(), i.getFinalQuantity(), i.getId()));
            }
        }
        if (picks.isEmpty()) {
            throw ApiException.rule("Nothing could be packed. Cancel the order instead.");
        }
        List<Allocation> allocations = inventory.deduct(o.getStoreId(), MovementType.ONLINE_ORDER_PICK,
                new Reference(REFERENCE_TYPE, o.getId(), user.id(), "Packed " + o.getOrderNumber()), picks);
        for (Allocation a : allocations) {
            OnlineOrderItem i = item(o, a.lineId());
            i.setUnitCost(a.unitCost());
            i.setCostAmount(a.costAmount());
        }
        Instant now = clock.instant();
        Store store = stores.requireStore(o.getStoreId());
        int holdDays = settings.integer(o.getStoreId(), "shop.pickupHoldDays", 3);
        o.setFinalTotal(MoneyPolicy.amount(total));
        o.setFinalTaxTotal(MoneyPolicy.amount(tax));
        o.setStockPicked(true);
        o.setStatus(OnlineOrder.Status.READY_FOR_PICKUP);
        o.setReadyAt(now);
        o.setCollectBy(now.plus(Duration.ofDays(holdDays)));
        String until = TIME.format(o.getCollectBy().atZone(ZoneId.of(store.getTimezone())));
        views.event(o, "Ready for pickup", "STAFF", user.id());
        notifications.notify(o.getCustomerAccountId(), o.getId(), "ORDER", "Ready for pickup: " + o.getOrderNumber(),
                "Collect at " + store.getName() + " by " + until + ". Show pickup code " + o.getPickupCode()
                        + " and pay " + o.getFinalTotal().toPlainString() + " at the counter. Tap I am here when you arrive.");
        audit.record(AuditService.Entry.success("ONLINE_ORDER_READY", "OnlineOrder", o.getId(), o.getOrderNumber() + " "
                + o.getFinalTotal()).withStore(o.getStoreId()));
        return views.forStaff(o);
    }

    @Transactional
    public OrderDto cancel(Long id, String reason) {
        CurrentUser user = staff("ONLINE_ORDER_MANAGE");
        OnlineOrder o = lock(id, user);
        if (o.getStatus() == OnlineOrder.Status.COLLECTED || o.getStatus() == OnlineOrder.Status.CANCELLED
                || o.getStatus() == OnlineOrder.Status.EXPIRED) {
            throw ApiException.invalidState("This order is already " + o.getStatus().name().toLowerCase() + ".");
        }
        releaseStock(o, user.id(), "Cancelled " + o.getOrderNumber());
        o.setStatus(OnlineOrder.Status.CANCELLED);
        o.setCancelledAt(clock.instant());
        o.setCancelReason(reason.trim());
        views.event(o, "Cancelled by the store: " + reason.trim(), "STAFF", user.id());
        notifications.notify(o.getCustomerAccountId(), o.getId(), "ORDER", "Order " + o.getOrderNumber() + " cancelled",
                "The store cancelled your order (" + reason.trim() + "). Nothing was charged.");
        audit.record(AuditService.Entry.success("ONLINE_ORDER_CANCELLED", "OnlineOrder", o.getId(), reason.trim())
                .withStore(o.getStoreId()));
        return views.forStaff(o);
    }

    /** Payment at the counter turns the packed order into a sale (exactly once per Idempotency-Key). */
    public IdempotencyService.Outcome<SaleDto> collect(Long id, CollectRequest request, String key) {
        CurrentUser user = staff("POS_SELL");
        IdempotencyService.Request idem = idempotency.request(user.id(), "ONLINE_COLLECT", key, Map.of("order", id, "body", request));
        return idempotency.execute(idem, () -> tx.execute(s -> doCollect(id, request, idem, user)),
                saleQueries::loadDto);
    }

    private SaleDto doCollect(Long id, CollectRequest request, IdempotencyService.Request idem, CurrentUser user) {
        IdempotencyRecord record = idempotency.register(idem);
        OnlineOrder o = lock(id, user);
        requireStatus(o, OnlineOrder.Status.READY_FOR_PICKUP);
        if (!o.getPickupCode().equals(request.pickupCode())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The pickup code does not match this order.");
        }
        List<PrepricedLine> lines = new ArrayList<>();
        Map<Long, Product> productMap = products.loadForPricing(o.getItems().stream()
                .map(OnlineOrderItem::getFinalProductId).filter(java.util.Objects::nonNull).toList());
        for (OnlineOrderItem i : o.getItems()) {
            if (i.getFinalProductId() == null) {
                continue;
            }
            Product p = productMap.get(i.getFinalProductId());
            BigDecimal total = i.getFinalLineTotal();
            BigDecimal tax = i.getFinalTaxAmount();
            BigDecimal net = o.isPricesIncludeTax() ? total : total.subtract(tax);
            boolean substitute = i.getPickStatus() == OnlineOrderItem.PickStatus.SUBSTITUTED;
            BigDecimal promo = substitute ? MoneyPolicy.ZERO
                    : i.getFinalQuantity().compareTo(i.getQuantity()) == 0 ? i.getPromoDiscountAmount()
                    : scale(i.getPromoDiscountAmount(), i.getFinalQuantity(), i.getQuantity());
            BigDecimal gross = net.add(promo);
            BigDecimal unitPrice = substitute
                    ? gross.divide(i.getFinalQuantity(), MoneyPolicy.AMOUNT_SCALE, RoundingMode.HALF_UP) : i.getUnitPrice();
            lines.add(new PrepricedLine(p.getId(), p.getSku(), substitute ? p.primaryBarcode().map(b -> b.getBarcode()).orElse(null)
                    : i.getBarcodeSnapshot(), p.getName(), p.getCategory().getId(), p.getUnitOfMeasure().name(),
                    i.getFinalQuantity(), unitPrice, gross, promo, net, substitute ? i.getSubstituteTaxRate() : i.getTaxRatePercent(),
                    tax, total, i.getUnitCost(), i.getCostAmount()));
        }
        SaleDto sale = prepricedSales.record(new PrepricedSale(request.registerId(), o.getCustomerId(), o.getId(),
                o.isPricesIncludeTax(), lines, request.payments(), request.expectedTotal()), user);
        o.setStatus(OnlineOrder.Status.COLLECTED);
        o.setCollectedAt(clock.instant());
        o.setSaleId(sale.id());
        views.event(o, "Collected. Thank you!", "STAFF", user.id());
        notifications.notify(o.getCustomerAccountId(), o.getId(), "ORDER", "Thanks for collecting " + o.getOrderNumber(),
                "Receipt " + sale.receiptNumber() + ". Points earned: " + sale.loyaltyPointsEarned() + ".");
        idempotency.complete(record, "SALE", sale.id(), 201);
        return sale;
    }

    /** Ready orders not collected in time expire: the goods go back to stock. */
    @Scheduled(cron = "0 */10 * * * *")
    public void expireUncollected() {
        for (OnlineOrder candidate : orders.findByStatusAndCollectByBefore(OnlineOrder.Status.READY_FOR_PICKUP, clock.instant())) {
            try {
                tx.executeWithoutResult(s -> {
                    OnlineOrder o = orders.lockById(candidate.getId()).orElseThrow();
                    if (o.getStatus() != OnlineOrder.Status.READY_FOR_PICKUP) {
                        return;
                    }
                    o.getItems().size();
                    releaseStock(o, o.getHandledBy() == null ? 1L : o.getHandledBy(), "Not collected " + o.getOrderNumber());
                    o.setStatus(OnlineOrder.Status.EXPIRED);
                    views.event(o, "Not collected in time; the order has expired", "SYSTEM", null);
                    notifications.notify(o.getCustomerAccountId(), o.getId(), "ORDER", "Order " + o.getOrderNumber() + " expired",
                            "It was not collected in time, so the items went back on the shelf. Nothing was charged.");
                });
            } catch (RuntimeException e) {
                log.warn("Could not expire online order {}: {}", candidate.getId(), e.getMessage());
            }
        }
    }

    @Override
    public String orderNumber(Long onlineOrderId) {
        return orders.findById(onlineOrderId).map(OnlineOrder::getOrderNumber).orElse(null);
    }

    // ----- helpers -------------------------------------------------------------------------------

    private void releaseStock(OnlineOrder o, Long userId, String reason) {
        if (!o.isStockPicked()) {
            return;
        }
        List<Receipt> back = new ArrayList<>();
        for (OnlineOrderItem i : o.getItems()) {
            if (i.getFinalProductId() != null && i.getFinalQuantity() != null && i.getFinalQuantity().signum() > 0) {
                Long batchId = inventory.batchOf(REFERENCE_TYPE, o.getId(), i.getId()).orElse(null);
                back.add(new Receipt(i.getFinalProductId(), i.getFinalQuantity(), i.getUnitCost(), null, null, i.getId(), batchId, null));
            }
        }
        inventory.receive(o.getStoreId(), MovementType.ONLINE_ORDER_RELEASE, new Reference(REFERENCE_TYPE, o.getId(), userId, reason), back);
        o.setStockPicked(false);
    }

    private static BigDecimal scale(BigDecimal amount, BigDecimal part, BigDecimal whole) {
        return MoneyPolicy.amount(amount.multiply(part).divide(whole, 8, RoundingMode.HALF_UP));
    }

    private static void clearSubstitute(OnlineOrderItem item) {
        item.setSubstituteProductId(null);
        item.setSubstituteName(null);
        item.setSubstituteQuantity(null);
        item.setSubstituteLineTotal(null);
        item.setSubstituteTaxRate(null);
    }

    private static OnlineOrderItem item(OnlineOrder o, Long itemId) {
        return o.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Order line", itemId));
    }

    private OnlineOrder load(Long id) {
        OnlineOrder o = orders.findWithItemsById(id).orElseThrow(() -> ApiException.notFound("Order", id));
        currentUser.require().assertStoreAccess(o.getStoreId(), "Order", id);
        return o;
    }

    private OnlineOrder lock(Long id, CurrentUser user) {
        OnlineOrder o = orders.lockById(id).orElseThrow(() -> ApiException.notFound("Order", id));
        user.assertStoreAccess(o.getStoreId(), "Order", id);
        o.getItems().size();
        return o;
    }

    private static void requireStatus(OnlineOrder o, OnlineOrder.Status expected) {
        if (o.getStatus() != expected) {
            throw ApiException.invalidState("The order is " + o.getStatus().name().toLowerCase().replace('_', ' ')
                    + "; this needs it to be " + expected.name().toLowerCase().replace('_', ' ') + ".");
        }
    }

    private CurrentUser staff(String... anyPermission) {
        CurrentUser user = currentUser.require();
        if (!user.hasAny(anyPermission)) {
            throw ApiException.forbidden("You do not have permission to handle online orders.");
        }
        return user;
    }
}
