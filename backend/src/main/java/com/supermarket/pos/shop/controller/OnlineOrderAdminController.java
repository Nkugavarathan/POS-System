package com.supermarket.pos.shop.controller;

import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.sales.dto.SalesDtos.SaleDto;
import com.supermarket.pos.shop.dto.ShopDtos.BoardSummaryDto;
import com.supermarket.pos.shop.dto.ShopDtos.CancelRequest;
import com.supermarket.pos.shop.dto.ShopDtos.CollectRequest;
import com.supermarket.pos.shop.dto.ShopDtos.OrderDto;
import com.supermarket.pos.shop.dto.ShopDtos.OrderSummaryDto;
import com.supermarket.pos.shop.dto.ShopDtos.PickRequest;
import com.supermarket.pos.shop.dto.ShopDtos.RescueDealDto;
import com.supermarket.pos.shop.dto.ShopDtos.RescueRunDto;
import com.supermarket.pos.shop.dto.ShopDtos.SubstituteRequest;
import com.supermarket.pos.shop.entity.OnlineOrder;
import com.supermarket.pos.shop.service.FulfilmentService;
import com.supermarket.pos.shop.service.RescueDealService;
import com.supermarket.pos.shop.service.ShopCatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@Tag(name = "Online orders (store)", description = "Packing board, substitutions, pickup and rescue deals")
@RestController
@RequestMapping("/api/v1/online-orders")
public class OnlineOrderAdminController {

    private final FulfilmentService fulfilment;
    private final RescueDealService rescue;
    private final ShopCatalogService catalog;
    private final CurrentUserProvider currentUser;

    public OnlineOrderAdminController(FulfilmentService fulfilment, RescueDealService rescue, ShopCatalogService catalog,
                                      CurrentUserProvider currentUser) {
        this.fulfilment = fulfilment;
        this.rescue = rescue;
        this.catalog = catalog;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<OrderSummaryDto> board(@RequestParam(required = false) Set<OnlineOrder.Status> status,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return fulfilment.board(status, q, page, size);
    }

    @GetMapping("/summary")
    public BoardSummaryDto summary() {
        return fulfilment.summary();
    }

    @GetMapping("/collect")
    public OrderDto findForCollection(@RequestParam String code) {
        return fulfilment.findForCollection(code);
    }

    @GetMapping("/{id}")
    public OrderDto get(@PathVariable Long id) {
        return fulfilment.get(id);
    }

    @PostMapping("/{id}/confirm")
    public OrderDto confirm(@PathVariable Long id) {
        return fulfilment.confirm(id);
    }

    @PostMapping("/{id}/start-packing")
    public OrderDto startPacking(@PathVariable Long id) {
        return fulfilment.startPacking(id);
    }

    @PostMapping("/{id}/items/{itemId}/pick")
    public OrderDto pick(@PathVariable Long id, @PathVariable Long itemId, @Valid @RequestBody(required = false) PickRequest request) {
        return fulfilment.pick(id, itemId, request == null ? null : request.pickedQuantity());
    }

    @PostMapping("/{id}/items/{itemId}/substitute")
    public OrderDto substitute(@PathVariable Long id, @PathVariable Long itemId, @Valid @RequestBody SubstituteRequest request) {
        return fulfilment.proposeSubstitute(id, itemId, request.productId(), request.quantity());
    }

    @PostMapping("/{id}/items/{itemId}/unavailable")
    public OrderDto unavailable(@PathVariable Long id, @PathVariable Long itemId) {
        return fulfilment.markUnavailable(id, itemId);
    }

    @PostMapping("/{id}/items/{itemId}/reset")
    public OrderDto reset(@PathVariable Long id, @PathVariable Long itemId) {
        return fulfilment.resetItem(id, itemId);
    }

    @PostMapping("/{id}/ready")
    public OrderDto ready(@PathVariable Long id) {
        return fulfilment.markReady(id);
    }

    @PostMapping("/{id}/cancel")
    public OrderDto cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        return fulfilment.cancel(id, request.reason());
    }

    @PostMapping("/{id}/collect")
    public ResponseEntity<SaleDto> collect(@PathVariable Long id,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                           @Valid @RequestBody CollectRequest request) {
        var outcome = fulfilment.collect(id, request, key);
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(outcome.value());
    }

    @PreAuthorize("hasAnyAuthority('RESCUE_DEAL_MANAGE','INVENTORY_VIEW','ONLINE_ORDER_MANAGE')")
    @GetMapping("/rescue-deals")
    public List<RescueDealDto> rescueDeals() {
        return catalog.rescueDeals(currentUser.require().requireActiveStore());
    }

    @PreAuthorize("hasAuthority('RESCUE_DEAL_MANAGE')")
    @PostMapping("/rescue-deals/run")
    public RescueRunDto runRescue() {
        return rescue.run(currentUser.require().requireActiveStore());
    }
}
