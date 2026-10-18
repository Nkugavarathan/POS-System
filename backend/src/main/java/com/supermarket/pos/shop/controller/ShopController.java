package com.supermarket.pos.shop.controller;

import com.supermarket.pos.auth.jwt.AuthCookies;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.error.ProblemFactory;
import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.shop.dto.ShopDtos.BuyAgainDto;
import com.supermarket.pos.shop.dto.ShopDtos.CartQuoteDto;
import com.supermarket.pos.shop.dto.ShopDtos.CartQuoteRequest;
import com.supermarket.pos.shop.dto.ShopDtos.LinkCardRequest;
import com.supermarket.pos.shop.dto.ShopDtos.NotificationDto;
import com.supermarket.pos.shop.dto.ShopDtos.OrderDto;
import com.supermarket.pos.shop.dto.ShopDtos.OrderSummaryDto;
import com.supermarket.pos.shop.dto.ShopDtos.PlaceOrderRequest;
import com.supermarket.pos.shop.dto.ShopDtos.PreferredStoreRequest;
import com.supermarket.pos.shop.dto.ShopDtos.RegisterRequest;
import com.supermarket.pos.shop.dto.ShopDtos.RescueDealDto;
import com.supermarket.pos.shop.dto.ShopDtos.ShopCategoryDto;
import com.supermarket.pos.shop.dto.ShopDtos.ShopLoginRequest;
import com.supermarket.pos.shop.dto.ShopDtos.ShopMeDto;
import com.supermarket.pos.shop.dto.ShopDtos.ShopProductDto;
import com.supermarket.pos.shop.dto.ShopDtos.ShopStoreDto;
import com.supermarket.pos.shop.dto.ShopDtos.SubstitutionDecision;
import com.supermarket.pos.shop.service.CurrentCustomerProvider;
import com.supermarket.pos.shop.service.CustomerAuthService;
import com.supermarket.pos.shop.service.CustomerAuthService.Session;
import com.supermarket.pos.shop.service.NotificationService;
import com.supermarket.pos.shop.service.OnlineOrderService;
import com.supermarket.pos.shop.service.ShopCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Online shop API. /public needs no sign-in; everything else needs a customer session. */
@Tag(name = "Online shop", description = "Catalog, customer accounts, click & collect orders, notifications")
@RestController
@RequestMapping("/api/v1/shop")
public class ShopController {

    private final ShopCatalogService catalog;
    private final CustomerAuthService auth;
    private final OnlineOrderService orders;
    private final NotificationService notifications;
    private final CurrentCustomerProvider currentCustomer;
    private final AuthCookies cookies;

    public ShopController(ShopCatalogService catalog, CustomerAuthService auth, OnlineOrderService orders,
                          NotificationService notifications, CurrentCustomerProvider currentCustomer, AuthCookies cookies) {
        this.catalog = catalog;
        this.auth = auth;
        this.orders = orders;
        this.notifications = notifications;
        this.currentCustomer = currentCustomer;
        this.cookies = cookies;
    }

    // ----- public ----------------------------------------------------------------------------

    @GetMapping("/public/stores")
    public List<ShopStoreDto> stores() {
        return catalog.stores();
    }

    @GetMapping("/public/categories")
    public List<ShopCategoryDto> categories() {
        return catalog.categories();
    }

    @GetMapping("/public/products")
    public PageResponse<ShopProductDto> products(@RequestParam Long storeId,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(required = false) Long categoryId,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size,
                                                 @RequestParam(required = false) List<String> sort) {
        return catalog.products(storeId, q, categoryId, page, size, sort);
    }

    @GetMapping("/public/products/{id}")
    public ShopProductDto product(@PathVariable Long id, @RequestParam Long storeId) {
        return catalog.product(storeId, id);
    }

    @Operation(summary = "Near-expiry batches on automatic discount at this store")
    @GetMapping("/public/rescue-deals")
    public List<RescueDealDto> rescueDeals(@RequestParam Long storeId) {
        return catalog.rescueDeals(storeId);
    }

    @PostMapping("/public/cart/quote")
    public CartQuoteDto quote(@Valid @RequestBody CartQuoteRequest request) {
        return catalog.quote(request);
    }

    // ----- session -----------------------------------------------------------------------------

    @PostMapping("/auth/register")
    public ResponseEntity<ShopMeDto> register(@Valid @RequestBody RegisterRequest request) {
        return withCookies(auth.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ShopMeDto> login(@Valid @RequestBody ShopLoginRequest request) {
        return withCookies(auth.login(request), HttpStatus.OK);
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest http) {
        try {
            return withCookies(auth.refresh(cookies.read(http, AuthCookies.SHOP_REFRESH_COOKIE).orElse(null)), HttpStatus.OK);
        } catch (ApiException e) {
            if (e.getCode() != ErrorCode.REFRESH_TOKEN_INVALID) {
                throw e;
            }
            HttpHeaders headers = new HttpHeaders();
            cookies.clearShop(headers);
            return ResponseEntity.status(e.getCode().status()).headers(headers)
                    .body(ProblemFactory.create(e.getCode(), e.getMessage(), http.getRequestURI()));
        }
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        auth.logout(cookies.read(http, AuthCookies.SHOP_REFRESH_COOKIE).orElse(null));
        HttpHeaders headers = new HttpHeaders();
        cookies.clearShop(headers);
        return ResponseEntity.noContent().headers(headers).build();
    }

    // ----- account -----------------------------------------------------------------------------

    @GetMapping("/me")
    public ShopMeDto me() {
        return auth.me();
    }

    @PutMapping("/me/store")
    public ShopMeDto preferredStore(@Valid @RequestBody PreferredStoreRequest request) {
        return auth.setPreferredStore(request.storeId());
    }

    @Operation(summary = "Connect an in-store loyalty card (card number + phone on file must match)")
    @PostMapping("/me/loyalty-card")
    public ShopMeDto linkCard(@Valid @RequestBody LinkCardRequest request) {
        return auth.linkCard(request);
    }

    @Operation(summary = "Running low? Products you buy regularly, with the next purchase predicted")
    @GetMapping("/me/buy-again")
    public List<BuyAgainDto> buyAgain(@RequestParam Long storeId) {
        return orders.buyAgain(storeId);
    }

    @GetMapping("/notifications")
    public PageResponse<NotificationDto> notifications(@RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return notifications.list(currentCustomer.require().accountId(), page, size);
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unread() {
        return Map.of("unread", notifications.unread(currentCustomer.require().accountId()));
    }

    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable Long id) {
        notifications.markRead(currentCustomer.require().accountId(), id);
    }

    @PostMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll() {
        notifications.markAllRead(currentCustomer.require().accountId());
    }

    // ----- orders ------------------------------------------------------------------------------

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto place(@Valid @RequestBody PlaceOrderRequest request) {
        return orders.place(request);
    }

    @GetMapping("/orders")
    public PageResponse<OrderSummaryDto> myOrders(@RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size) {
        return orders.mine(page, size);
    }

    @GetMapping("/orders/{number}")
    public OrderDto order(@PathVariable String number) {
        return orders.get(number);
    }

    @PostMapping("/orders/{number}/cancel")
    public OrderDto cancel(@PathVariable String number) {
        return orders.cancel(number);
    }

    @Operation(summary = "Accept or decline a substitute proposed while packing")
    @PostMapping("/orders/{number}/items/{itemId}/substitution")
    public OrderDto decide(@PathVariable String number, @PathVariable Long itemId,
                           @Valid @RequestBody SubstitutionDecision decision) {
        return orders.decide(number, itemId, decision.accept());
    }

    @Operation(summary = "I am at the store (pickup check-in)")
    @PostMapping("/orders/{number}/arrived")
    public OrderDto arrived(@PathVariable String number) {
        return orders.arrived(number);
    }

    private ResponseEntity<ShopMeDto> withCookies(Session session, HttpStatus status) {
        HttpHeaders headers = new HttpHeaders();
        cookies.writeShopSession(headers, session.accessToken(), session.refreshToken());
        headers.setCacheControl("no-store");
        return ResponseEntity.status(status).headers(headers).body(session.me());
    }
}
