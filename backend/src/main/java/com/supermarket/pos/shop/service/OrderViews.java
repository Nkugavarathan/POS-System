package com.supermarket.pos.shop.service;

import com.supermarket.pos.customers.entity.Customer;
import com.supermarket.pos.customers.service.CustomerService;
import com.supermarket.pos.customers.service.PhoneNumbers;
import com.supermarket.pos.shop.dto.ShopDtos.OrderDto;
import com.supermarket.pos.shop.dto.ShopDtos.OrderEventDto;
import com.supermarket.pos.shop.dto.ShopDtos.OrderLineDto;
import com.supermarket.pos.shop.dto.ShopDtos.OrderSummaryDto;
import com.supermarket.pos.shop.dto.ShopDtos.ProgressDto;
import com.supermarket.pos.shop.dto.ShopDtos.SubstituteDto;
import com.supermarket.pos.shop.entity.OnlineOrder;
import com.supermarket.pos.shop.entity.OnlineOrderEvent;
import com.supermarket.pos.shop.entity.OnlineOrderItem;
import com.supermarket.pos.shop.repository.OnlineOrderEventRepository;
import com.supermarket.pos.store.entity.Store;
import com.supermarket.pos.store.service.StoreService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Builds order views for customers (no staff data) and for staff (with customer contact). */
@Component
public class OrderViews {

    private final OnlineOrderEventRepository events;
    private final StoreService stores;
    private final CustomerService customers;
    private final Clock clock;

    public OrderViews(OnlineOrderEventRepository events, StoreService stores, CustomerService customers, Clock clock) {
        this.events = events;
        this.stores = stores;
        this.customers = customers;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OrderDto forCustomer(OnlineOrder o) {
        return build(o, false);
    }

    @Transactional(readOnly = true)
    public OrderDto forStaff(OnlineOrder o) {
        return build(o, true);
    }

    public static ProgressDto progress(OnlineOrder o) {
        int resolved = (int) o.getItems().stream().filter(OnlineOrderItem::isResolved).count();
        int pending = (int) o.getItems().stream()
                .filter(i -> i.getPickStatus() == OnlineOrderItem.PickStatus.SUBSTITUTE_PROPOSED).count();
        return new ProgressDto(resolved, o.getItems().size(), pending);
    }

    public OrderSummaryDto summary(OnlineOrder o, boolean staff) {
        String customerName = staff ? customers.require(o.getCustomerId()).getFullName() : null;
        return new OrderSummaryDto(o.getId(), o.getOrderNumber(), o.getStatus(), o.getPlacedAt(), o.getPickupAfter(),
                o.getArrivedAt(), o.getItems().size(), o.payableTotal(), stores.requireStore(o.getStoreId()).getName(),
                customerName, progress(o));
    }

    private OrderDto build(OnlineOrder o, boolean staff) {
        Store store = stores.requireStore(o.getStoreId());
        Customer customer = staff ? customers.require(o.getCustomerId()) : null;
        List<OrderLineDto> lines = o.getItems().stream().map(i -> new OrderLineDto(i.getId(), i.getLineNumber(),
                i.getProductId(), i.getProductNameSnapshot(), i.getSkuSnapshot(), staff ? i.getBarcodeSnapshot() : null,
                i.getUnitOfMeasure(), i.getQuantity(), i.getUnitPrice(), i.getPromoDiscountAmount(), i.getPromotionName(),
                i.getLineTotal(), i.getSubstitutionPreference(), i.getPickStatus(), i.getPickedQuantity(),
                i.getSubstituteProductId() == null ? null : new SubstituteDto(i.getSubstituteProductId(),
                        i.getSubstituteName(), i.getSubstituteQuantity(), i.getSubstituteLineTotal()),
                i.getFinalLineTotal())).toList();
        List<OrderEventDto> timeline = events.findByOnlineOrderIdOrderByIdAsc(o.getId()).stream()
                .map(e -> new OrderEventDto(e.getStatus(), e.getMessage(), e.getActorType(), e.getCreatedAt())).toList();
        Instant now = clock.instant();
        boolean canCancel = o.getStatus() == OnlineOrder.Status.PLACED || o.getStatus() == OnlineOrder.Status.CONFIRMED;
        boolean canCheckIn = o.getStatus() == OnlineOrder.Status.READY_FOR_PICKUP && o.getArrivedAt() == null
                && (o.getCollectBy() == null || o.getCollectBy().isAfter(now));
        return new OrderDto(o.getId(), o.getOrderNumber(), o.getStatus(), ShopCatalogService.toStore(store),
                // the pickup code is shown to the customer; staff verify it, so they do not see it
                staff ? null : o.getPickupCode(), o.getPickupAfter(), o.getPlacedAt(), o.getReadyAt(), o.getCollectBy(),
                o.getArrivedAt(), o.getCollectedAt(), o.getCancelledAt(), o.getCancelReason(), o.getCurrency(),
                o.isPricesIncludeTax(), o.getSubtotal(), o.getDiscountTotal(), o.getTaxTotal(), o.getGrandTotal(),
                o.getFinalTotal(), o.payableTotal(), progress(o), o.getCustomerNote(),
                customer == null ? null : customer.getFullName(),
                customer == null ? null : PhoneNumbers.mask(customer.getPhone()), staff ? o.getSaleId() : null,
                lines, timeline, canCancel, canCheckIn, o.getVersion());
    }

    /** Appends a timeline step. */
    public OnlineOrderEvent event(OnlineOrder o, String message, String actorType, Long actorId) {
        OnlineOrderEvent e = new OnlineOrderEvent();
        e.setOnlineOrderId(o.getId());
        e.setStatus(o.getStatus());
        e.setMessage(message.length() > 255 ? message.substring(0, 255) : message);
        e.setActorType(actorType);
        e.setActorId(actorId);
        e.setCreatedAt(clock.instant());
        return events.save(e);
    }
}
