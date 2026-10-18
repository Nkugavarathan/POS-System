package com.supermarket.pos.shop.service;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.common.sequence.SequenceService;
import com.supermarket.pos.common.web.PageRequests;
import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.inventory.service.InventoryService;
import com.supermarket.pos.products.entity.Product;
import com.supermarket.pos.products.service.ProductService;
import com.supermarket.pos.sales.service.PricingEngine;
import com.supermarket.pos.sales.service.PricingEngine.LineInput;
import com.supermarket.pos.sales.service.PricingEngine.PricedCart;
import com.supermarket.pos.sales.service.PricingEngine.PricedLine;
import com.supermarket.pos.sales.service.PrepricedSaleService;
import com.supermarket.pos.sales.service.PrepricedSaleService.ProductHistory;
import com.supermarket.pos.shop.dto.ShopDtos.BuyAgainDto;
import com.supermarket.pos.shop.dto.ShopDtos.CartItem;
import com.supermarket.pos.shop.dto.ShopDtos.OrderDto;
import com.supermarket.pos.shop.dto.ShopDtos.OrderSummaryDto;
import com.supermarket.pos.shop.dto.ShopDtos.PlaceOrderRequest;
import com.supermarket.pos.shop.dto.ShopDtos.ShopProductDto;
import com.supermarket.pos.shop.entity.CustomerAccount;
import com.supermarket.pos.shop.entity.OnlineOrder;
import com.supermarket.pos.shop.entity.OnlineOrderItem;
import com.supermarket.pos.shop.repository.CustomerAccountRepository;
import com.supermarket.pos.shop.repository.OnlineOrderRepository;
import com.supermarket.pos.shop.service.CurrentCustomerProvider.CurrentCustomer;
import com.supermarket.pos.store.entity.Store;
import com.supermarket.pos.store.service.SettingsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Customer side of click &amp; collect: place, track, cancel, decide substitutions, check in. */
@Service
public class OnlineOrderService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OnlineOrderRepository orders;
    private final CustomerAccountRepository accounts;
    private final ShopCatalogService catalog;
    private final PricingEngine pricing;
    private final InventoryService inventory;
    private final ProductService products;
    private final PrepricedSaleService saleHistory;
    private final SequenceService sequences;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final OrderViews views;
    private final CurrentCustomerProvider currentCustomer;
    private final EntityManager entityManager;
    private final Clock clock;

    public OnlineOrderService(OnlineOrderRepository orders, CustomerAccountRepository accounts, ShopCatalogService catalog,
                              PricingEngine pricing, InventoryService inventory, ProductService products,
                              PrepricedSaleService saleHistory, SequenceService sequences, SettingsService settings,
                              NotificationService notifications, OrderViews views, CurrentCustomerProvider currentCustomer,
                              EntityManager entityManager, Clock clock) {
        this.orders = orders;
        this.accounts = accounts;
        this.catalog = catalog;
        this.pricing = pricing;
        this.inventory = inventory;
        this.products = products;
        this.saleHistory = saleHistory;
        this.sequences = sequences;
        this.settings = settings;
        this.notifications = notifications;
        this.views = views;
        this.currentCustomer = currentCustomer;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public OrderDto place(PlaceOrderRequest request) {
        CurrentCustomer customer = currentCustomer.require();
        Store store = catalog.requireShopStore(request.storeId());
        int maxLines = settings.integer(store.getId(), "shop.maxLinesPerOrder", 60);
        if (request.items().size() > maxLines) {
            throw ApiException.rule("An order can have at most " + maxLines + " different products.");
        }
        // Serialize orders of one account: a double-tapped "Place order" cannot create two orders.
        CustomerAccount account = accounts.findById(customer.accountId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Please sign in."));
        entityManager.lock(account, LockModeType.PESSIMISTIC_WRITE);
        Instant now = clock.instant();
        OnlineOrder duplicate = recentIdentical(account.getId(), store.getId(), request.items(), now);
        if (duplicate != null) {
            return views.forCustomer(duplicate);
        }

        PricedCart cart = pricing.price(store.getId(), request.items().stream()
                .map(i -> new LineInput(i.productId(), i.quantity(), null, null)).toList(), null, store.isPricesIncludeTax());
        if (cart.grandTotal().compareTo(MoneyPolicy.amount(request.expectedTotal())) != 0) {
            throw new ApiException(ErrorCode.QUOTE_MISMATCH, "Prices changed: the total is now " + cart.grandTotal()
                    + ". Please review your basket.", Map.of("grandTotal", cart.grandTotal().toPlainString()));
        }
        Map<Long, BigDecimal> requested = new LinkedHashMap<>();
        cart.lines().forEach(l -> requested.merge(l.product().getId(), l.quantity(), BigDecimal::add));
        Map<Long, Product> productMap = cart.lines().stream()
                .collect(Collectors.toMap(l -> l.product().getId(), PricedLine::product, (a, b) -> a));
        var shortages = inventory.shortages(store.getId(), requested, productMap);
        if (!shortages.isEmpty()) {
            throw new ApiException(ErrorCode.INSUFFICIENT_STOCK, "Some items just sold out. Update your basket.",
                    Map.of("productIds", new ArrayList<>(shortages.keySet())));
        }

        Instant pickupAfter = request.pickupAfter();
        if (pickupAfter != null && (pickupAfter.isBefore(now) || pickupAfter.isAfter(now.plus(Duration.ofDays(7))))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose a pickup time within the next 7 days.");
        }
        OnlineOrder order = new OnlineOrder();
        order.setStoreId(store.getId());
        order.setCustomerId(customer.customerId());
        order.setCustomerAccountId(account.getId());
        order.setPickupCode(String.format("%06d", RANDOM.nextInt(1_000_000)));
        order.setPickupAfter(pickupAfter);
        order.setCurrency(store.getCurrency());
        order.setPricesIncludeTax(cart.pricesIncludeTax());
        order.setSubtotal(cart.subtotal());
        order.setDiscountTotal(cart.discountTotal());
        order.setTaxTotal(cart.taxTotal());
        order.setGrandTotal(cart.grandTotal());
        order.setItemCount(cart.itemCount());
        order.setCustomerNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        order.setPlacedAt(now);
        order.setOrderNumber(sequences.nextNumber("ONLINE_ORDER", "WEB", store.getCode(), 6));
        for (int i = 0; i < cart.lines().size(); i++) {
            PricedLine l = cart.lines().get(i);
            CartItem in = request.items().get(i);
            OnlineOrderItem item = new OnlineOrderItem();
            item.setOrder(order);
            item.setLineNumber(l.lineNumber());
            item.setProductId(l.product().getId());
            item.setSkuSnapshot(l.product().getSku());
            item.setBarcodeSnapshot(l.barcode());
            item.setProductNameSnapshot(l.product().getName());
            item.setCategoryId(l.product().getCategory().getId());
            item.setUnitOfMeasure(l.product().getUnitOfMeasure().name());
            item.setQuantity(l.quantity());
            item.setUnitPrice(l.unitPrice());
            item.setGrossAmount(l.gross());
            item.setPromoDiscountAmount(l.promo());
            item.setPromotionName(l.promotion() == null ? null : l.promotion().getName());
            item.setNetAmount(l.net());
            item.setTaxRatePercent(l.taxRate());
            item.setTaxAmount(l.tax());
            item.setLineTotal(l.lineTotal());
            item.setSubstitutionPreference(in.substitutionPreference() == null
                    ? OnlineOrderItem.Preference.SUBSTITUTE : in.substitutionPreference());
            order.getItems().add(item);
        }
        OnlineOrder saved = orders.saveAndFlush(order);
        views.event(saved, "Order placed", "CUSTOMER", account.getId());
        notifications.notify(account.getId(), saved.getId(), "ORDER", "Order " + saved.getOrderNumber() + " received",
                "We will start packing soon at " + store.getName() + ". Pickup code: " + saved.getPickupCode() + ".");
        return views.forCustomer(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryDto> mine(Integer page, Integer size) {
        CurrentCustomer c = currentCustomer.require();
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "placedAt"));
        return PageResponse.of(orders.findByCustomerAccountIdOrderByPlacedAtDesc(c.accountId(), pageable),
                o -> views.summary(o, false));
    }

    @Transactional(readOnly = true)
    public OrderDto get(String orderNumber) {
        return views.forCustomer(own(orderNumber));
    }

    @Transactional
    public OrderDto cancel(String orderNumber) {
        CurrentCustomer c = currentCustomer.require();
        OnlineOrder order = lockOwn(orderNumber, c);
        if (order.getStatus() != OnlineOrder.Status.PLACED && order.getStatus() != OnlineOrder.Status.CONFIRMED) {
            throw ApiException.invalidState("Packing has started; please contact the store to cancel.");
        }
        order.setStatus(OnlineOrder.Status.CANCELLED);
        order.setCancelledAt(clock.instant());
        order.setCancelReason("Cancelled by customer");
        views.event(order, "Cancelled by you", "CUSTOMER", c.accountId());
        notifications.notify(c.accountId(), order.getId(), "ORDER", "Order " + order.getOrderNumber() + " cancelled",
                "Your order was cancelled. Nothing was charged.");
        return views.forCustomer(order);
    }

    /** Accept or decline a substitute proposed by the picker. */
    @Transactional
    public OrderDto decide(String orderNumber, Long itemId, boolean accept) {
        CurrentCustomer c = currentCustomer.require();
        OnlineOrder order = lockOwn(orderNumber, c);
        OnlineOrderItem item = order.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Order line", itemId));
        if (item.getPickStatus() != OnlineOrderItem.PickStatus.SUBSTITUTE_PROPOSED) {
            throw ApiException.invalidState("There is no substitute waiting for your answer on this line.");
        }
        item.setPickStatus(accept ? OnlineOrderItem.PickStatus.SUBSTITUTED : OnlineOrderItem.PickStatus.UNAVAILABLE);
        views.event(order, (accept ? "You accepted " + item.getSubstituteName() : "You declined the substitute")
                + " for " + item.getProductNameSnapshot(), "CUSTOMER", c.accountId());
        return views.forCustomer(order);
    }

    /** "I'm here": tells the store the customer has arrived to collect. */
    @Transactional
    public OrderDto arrived(String orderNumber) {
        CurrentCustomer c = currentCustomer.require();
        OnlineOrder order = lockOwn(orderNumber, c);
        if (order.getStatus() != OnlineOrder.Status.READY_FOR_PICKUP) {
            throw ApiException.invalidState("Your order is not ready for pickup yet.");
        }
        if (order.getArrivedAt() == null) {
            order.setArrivedAt(clock.instant());
            views.event(order, "You arrived at the store", "CUSTOMER", c.accountId());
        }
        return views.forCustomer(order);
    }

    /**
     * "Running low?" Products the customer buys repeatedly (in store with the loyalty card and
     * online), with the next purchase predicted from their own average interval.
     */
    @Transactional(readOnly = true)
    public List<BuyAgainDto> buyAgain(Long storeId) {
        CurrentCustomer c = currentCustomer.require();
        Store store = catalog.requireShopStore(storeId);
        Instant now = clock.instant();
        List<ProductHistory> history = saleHistory.productHistory(c.customerId());
        Map<Long, Product> productMap = products.loadForPricing(history.stream().map(ProductHistory::productId).toList());
        List<Product> active = productMap.values().stream().filter(Product::isActive).toList();
        Map<Long, ShopProductDto> cards = catalog.map(store, active);
        List<BuyAgainDto> result = new ArrayList<>();
        for (ProductHistory h : history) {
            ShopProductDto card = cards.get(h.productId());
            if (card == null) {
                continue;
            }
            Long interval = null;
            Long dueIn = null;
            if (h.purchases() >= 2) {
                long spanDays = Duration.between(h.first(), h.last()).toDays();
                interval = Math.max(1, spanDays / (h.purchases() - 1));
                dueIn = interval - Duration.between(h.last(), now).toDays();
            }
            boolean dueSoon = dueIn != null && dueIn <= Math.max(1, interval / 7);
            result.add(new BuyAgainDto(card.id(), card.name(), card.unitOfMeasure(), card.price(), card.offerPrice(),
                    h.purchases(), h.last(), interval, dueIn, dueSoon, card.stockLevel()));
        }
        result.sort(Comparator.comparing(BuyAgainDto::dueSoon).reversed()
                .thenComparing(b -> b.dueInDays() == null ? Long.MAX_VALUE : b.dueInDays())
                .thenComparing(BuyAgainDto::purchases, Comparator.reverseOrder()));
        return result.size() > 30 ? result.subList(0, 30) : result;
    }

    // ----- helpers -----------------------------------------------------------------------------

    private OnlineOrder own(String orderNumber) {
        CurrentCustomer c = currentCustomer.require();
        OnlineOrder order = orders.findWithItemsByOrderNumber(orderNumber)
                .orElseThrow(() -> ApiException.notFound("Order", orderNumber));
        if (!order.getCustomerAccountId().equals(c.accountId())) {
            throw ApiException.notFound("Order", orderNumber); // never reveal other customers' orders
        }
        return order;
    }

    private OnlineOrder lockOwn(String orderNumber, CurrentCustomer c) {
        OnlineOrder found = own(orderNumber);
        OnlineOrder locked = orders.lockById(found.getId()).orElseThrow();
        locked.getItems().size();
        return locked;
    }

    private OnlineOrder recentIdentical(Long accountId, Long storeId, List<CartItem> items, Instant now) {
        var recent = orders.findByCustomerAccountIdOrderByPlacedAtDesc(accountId,
                org.springframework.data.domain.PageRequest.of(0, 3)).getContent();
        String wanted = signature(items.stream().map(i -> i.productId() + ":" + MoneyPolicy.quantity(i.quantity())).toList());
        for (OnlineOrder o : recent) {
            if (o.getStoreId().equals(storeId) && o.getStatus() == OnlineOrder.Status.PLACED
                    && Duration.between(o.getPlacedAt(), now).toSeconds() < 120
                    && Objects.equals(wanted, signature(o.getItems().stream()
                    .map(i -> i.getProductId() + ":" + i.getQuantity()).toList()))) {
                return o;
            }
        }
        return null;
    }

    private static String signature(List<String> parts) {
        return parts.stream().sorted().collect(Collectors.joining(","));
    }
}
