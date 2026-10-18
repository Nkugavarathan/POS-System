package com.supermarket.pos.pos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.auth.entity.ApprovalAction;
import com.supermarket.pos.auth.service.ApprovalService;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.common.security.CurrentUser;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.pos.dto.PosDtos.HeldCartDto;
import com.supermarket.pos.pos.dto.PosDtos.HeldItem;
import com.supermarket.pos.pos.dto.PosDtos.HoldCartRequest;
import com.supermarket.pos.pos.dto.PosDtos.VoidDto;
import com.supermarket.pos.pos.dto.PosDtos.VoidItem;
import com.supermarket.pos.pos.dto.PosDtos.VoidRequest;
import com.supermarket.pos.pos.entity.CashierShift;
import com.supermarket.pos.pos.entity.HeldCart;
import com.supermarket.pos.pos.entity.HeldCartItem;
import com.supermarket.pos.pos.entity.TransactionVoid;
import com.supermarket.pos.pos.repository.HeldCartRepository;
import com.supermarket.pos.pos.repository.TransactionVoidRepository;
import com.supermarket.pos.store.service.SettingsService;
import com.supermarket.pos.users.service.UserAccountService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Held (parked) carts and pre-payment voids at the counter. */
@Service
public class CounterService {

    private final HeldCartRepository heldCarts;
    private final TransactionVoidRepository voids;
    private final ShiftService shifts;
    private final ApprovalService approvals;
    private final SettingsService settings;
    private final UserAccountService users;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CounterService(HeldCartRepository heldCarts, TransactionVoidRepository voids, ShiftService shifts,
                          ApprovalService approvals, SettingsService settings, UserAccountService users,
                          CurrentUserProvider currentUser, AuditService audit, ObjectMapper objectMapper, Clock clock) {
        this.heldCarts = heldCarts;
        this.voids = voids;
        this.shifts = shifts;
        this.approvals = approvals;
        this.settings = settings;
        this.users = users;
        this.currentUser = currentUser;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('POS_HOLD_CART')")
    @Transactional
    public HeldCartDto hold(HoldCartRequest request) {
        CurrentUser user = currentUser.require();
        Long storeId = user.requireActiveStore();
        CashierShift shift = shifts.lockOpenShift(user.id(), storeId, request.registerId());
        HeldCart cart = new HeldCart();
        cart.setStoreId(storeId);
        cart.setRegisterId(shift.getRegisterId());
        cart.setShiftId(shift.getId());
        cart.setHeldBy(user.id());
        cart.setCustomerId(request.customerId());
        cart.setLabel(request.label() == null || request.label().isBlank() ? null : request.label().trim());
        cart.setHeldAt(clock.instant());
        int line = 1;
        for (HeldItem item : request.items()) {
            HeldCartItem i = new HeldCartItem();
            i.setHeldCart(cart);
            i.setProductId(item.productId());
            i.setQuantity(MoneyPolicy.quantity(item.quantity()));
            i.setLineNumber(line++);
            cart.getItems().add(i);
        }
        HeldCart saved = heldCarts.saveAndFlush(cart);
        audit.record(AuditService.Entry.success("CART_HELD", "HeldCart", saved.getId(),
                saved.getItems().size() + " lines held").withStore(storeId));
        return toDto(saved, users.displayNames(List.of(user.id())));
    }

    @PreAuthorize("hasAuthority('POS_HOLD_CART')")
    @Transactional(readOnly = true)
    public List<HeldCartDto> held() {
        Long storeId = currentUser.require().requireActiveStore();
        List<HeldCart> carts = heldCarts.findByStoreIdAndStatusOrderByHeldAtDesc(storeId, HeldCart.Status.HELD);
        Map<Long, String> names = users.displayNames(carts.stream().map(HeldCart::getHeldBy).toList());
        return carts.stream().map(c -> toDto(c, names)).toList();
    }

    /** Marks the cart RESUMED exactly once (row lock) and returns its lines for re-quoting. */
    @PreAuthorize("hasAuthority('POS_HOLD_CART')")
    @Transactional
    public HeldCartDto resume(Long id) {
        CurrentUser user = currentUser.require();
        HeldCart cart = lockHeld(id, user);
        cart.setStatus(HeldCart.Status.RESUMED);
        cart.setResumedAt(clock.instant());
        cart.setResumedBy(user.id());
        audit.record(AuditService.Entry.success("CART_RESUMED", "HeldCart", id, "Held cart resumed").withStore(cart.getStoreId()));
        return toDto(cart, users.displayNames(List.of(cart.getHeldBy())));
    }

    @PreAuthorize("hasAuthority('POS_HOLD_CART')")
    @Transactional
    public HeldCartDto cancel(Long id) {
        CurrentUser user = currentUser.require();
        HeldCart cart = lockHeld(id, user);
        cart.setStatus(HeldCart.Status.CANCELLED);
        audit.record(AuditService.Entry.success("CART_HELD_CANCELLED", "HeldCart", id, "Held cart cancelled")
                .withStore(cart.getStoreId()));
        return toDto(cart, users.displayNames(List.of(cart.getHeldBy())));
    }

    /** Records lines removed before payment (loss-prevention trail). Approval rules from settings. */
    @PreAuthorize("hasAuthority('POS_SELL')")
    @Transactional
    public VoidDto recordVoid(VoidRequest request) {
        CurrentUser user = currentUser.require();
        Long storeId = user.requireActiveStore();
        CashierShift shift = shifts.lockOpenShift(user.id(), storeId, request.registerId());
        BigDecimal amount = MoneyPolicy.amount(request.items().stream().map(VoidItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        boolean needsApproval;
        ApprovalAction action;
        if (request.type() == TransactionVoid.Type.CART) {
            action = ApprovalAction.CART_VOID;
            needsApproval = settings.bool(storeId, "void.cartApprovalRequired", true);
        } else {
            action = ApprovalAction.LINE_VOID;
            needsApproval = amount.compareTo(settings.decimal(storeId, "void.lineApprovalThreshold", new BigDecimal("1000"))) > 0;
        }
        Long approvedBy = null;
        if (needsApproval && !user.has("VOID_APPROVE")) {
            if (request.approvalToken() == null || request.approvalToken().isBlank()) {
                throw new ApiException(ErrorCode.APPROVAL_REQUIRED, "Manager approval is required to void this.",
                        Map.of("action", action.name(), "value", amount.toPlainString()));
            }
            approvedBy = approvals.consume(request.approvalToken(), action, storeId, user.id(), null).approverId();
        }
        TransactionVoid v = new TransactionVoid();
        v.setStoreId(storeId);
        v.setRegisterId(shift.getRegisterId());
        v.setShiftId(shift.getId());
        v.setCashierId(user.id());
        v.setApprovedBy(approvedBy);
        v.setVoidType(request.type());
        v.setReasonCode(request.reasonCode().trim());
        v.setReasonText(request.reasonText());
        v.setItemsJson(toJson(request.items()));
        v.setEstimatedAmount(amount);
        v.setCreatedAt(clock.instant());
        TransactionVoid saved = voids.saveAndFlush(v);
        audit.record(AuditService.Entry.success(request.type() == TransactionVoid.Type.CART ? "CART_VOIDED" : "LINE_VOIDED",
                "TransactionVoid", saved.getId(), request.reasonCode() + " " + amount).withStore(storeId));
        return new VoidDto(saved.getId(), saved.getVoidType(), saved.getReasonCode(), saved.getEstimatedAmount(),
                saved.getApprovedBy(), saved.getCreatedAt());
    }

    /** Held carts older than the configured TTL expire (shift close expires them too). */
    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void expireOld() {
        Instant cutoff = clock.instant().minus(Duration.ofHours(settings.integer(null, "heldCart.ttlHours", 12)));
        heldCarts.findByStatusAndHeldAtBefore(HeldCart.Status.HELD, cutoff).forEach(c -> c.setStatus(HeldCart.Status.EXPIRED));
    }

    private HeldCart lockHeld(Long id, CurrentUser user) {
        HeldCart cart = heldCarts.lockById(id).orElseThrow(() -> ApiException.notFound("Held cart", id));
        user.assertStoreAccess(cart.getStoreId(), "Held cart", id);
        if (cart.getStatus() != HeldCart.Status.HELD) {
            throw ApiException.invalidState("This cart was already " + cart.getStatus().name().toLowerCase() + ".");
        }
        cart.getItems().size();
        return cart;
    }

    private static HeldCartDto toDto(HeldCart c, Map<Long, String> names) {
        List<HeldItem> items = c.getItems().stream().map(i -> new HeldItem(i.getProductId(), i.getQuantity())).toList();
        return new HeldCartDto(c.getId(), c.getLabel(), c.getCustomerId(), c.getStatus(), c.getHeldBy(),
                names.get(c.getHeldBy()), c.getHeldAt(), items.size(), items);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
