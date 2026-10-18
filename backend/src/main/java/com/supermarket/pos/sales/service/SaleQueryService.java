package com.supermarket.pos.sales.service;

import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.common.security.CurrentUser;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.common.web.PageRequests;
import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.customers.entity.Customer;
import com.supermarket.pos.customers.repository.CustomerRepository;
import com.supermarket.pos.payments.entity.Payment;
import com.supermarket.pos.payments.service.PaymentService;
import com.supermarket.pos.pos.service.ShiftActivity;
import com.supermarket.pos.sales.dto.SalesDtos.LineDto;
import com.supermarket.pos.sales.dto.SalesDtos.SaleDto;
import com.supermarket.pos.sales.dto.SalesDtos.SaleSummaryDto;
import com.supermarket.pos.sales.dto.SalesDtos.StoreInfo;
import com.supermarket.pos.sales.dto.SalesDtos.TotalsDto;
import com.supermarket.pos.sales.entity.Sale;
import com.supermarket.pos.sales.entity.SaleItem;
import com.supermarket.pos.sales.repository.SaleRepository;
import com.supermarket.pos.store.entity.Store;
import com.supermarket.pos.store.service.StoreService;
import com.supermarket.pos.users.service.UserAccountService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sale lookups, receipts and reprints. Cashiers with SALE_VIEW_OWN only see their own sales;
 * SALE_VIEW_ALL sees the whole store. Also reports what sales contributed to a cash shift.
 */
@Service
public class SaleQueryService implements ShiftActivity {

    private static final Map<String, String> SORTS = Map.of("completedAt", "completedAt", "grandTotal", "grandTotal");
    private static final Duration MAX_RANGE = Duration.ofDays(366);

    private final SaleRepository sales;
    private final PaymentService payments;
    private final StoreService stores;
    private final UserAccountService users;
    private final CustomerRepository customers;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;
    private final ObjectProvider<OnlineOrderNumbers> orderNumbers;
    private final Clock clock;

    public SaleQueryService(SaleRepository sales, PaymentService payments, StoreService stores, UserAccountService users,
                            CustomerRepository customers, CurrentUserProvider currentUser, AuditService audit,
                            ObjectProvider<OnlineOrderNumbers> orderNumbers, Clock clock) {
        this.sales = sales;
        this.payments = payments;
        this.stores = stores;
        this.users = users;
        this.customers = customers;
        this.currentUser = currentUser;
        this.audit = audit;
        this.orderNumbers = orderNumbers;
        this.clock = clock;
    }

    /** Resolves online order numbers for receipts; implemented by the shop module. */
    public interface OnlineOrderNumbers {
        String orderNumber(Long onlineOrderId);
    }

    @Transactional(readOnly = true)
    public SaleDto get(Long id) {
        Sale sale = sales.findWithItemsById(id).orElseThrow(() -> ApiException.notFound("Sale", id));
        checkAccess(sale, currentUser.require());
        return toDto(sale, false);
    }

    @Transactional(readOnly = true)
    public SaleDto byReceipt(String receiptNumber) {
        Sale sale = sales.findWithItemsByReceiptNumber(receiptNumber.trim())
                .orElseThrow(() -> ApiException.notFound("Receipt", receiptNumber));
        CurrentUser user = currentUser.require();
        if (!user.has("RETURN_CREATE")) {
            checkAccess(sale, user);
        } else {
            user.assertStoreAccess(sale.getStoreId(), "Receipt", receiptNumber);
        }
        return toDto(sale, false);
    }

    /** Reprints never create a sale or touch stock; they are counted and audited. */
    @Transactional
    public SaleDto reprint(Long id) {
        CurrentUser user = currentUser.require();
        if (!user.has("RECEIPT_REPRINT")) {
            throw ApiException.forbidden("Reprinting receipts requires RECEIPT_REPRINT.");
        }
        Sale sale = sales.findWithItemsById(id).orElseThrow(() -> ApiException.notFound("Sale", id));
        checkAccess(sale, user);
        sale.setReprintCount(sale.getReprintCount() + 1);
        audit.record(AuditService.Entry.success("RECEIPT_REPRINTED", "Sale", id,
                sale.getReceiptNumber() + " copy #" + sale.getReprintCount()).withStore(sale.getStoreId()));
        return toDto(sale, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<SaleSummaryDto> list(Instant from, Instant to, Long cashierId, Long registerId, Long customerId,
                                             Sale.Status status, Sale.Channel channel, String receipt, Integer page,
                                             Integer size, List<String> sort) {
        CurrentUser user = currentUser.require();
        if (!user.hasAny("SALE_VIEW_ALL", "SALE_VIEW_OWN")) {
            throw ApiException.forbidden("You cannot view sales.");
        }
        Long storeId = user.requireActiveStore();
        Instant end = to == null ? clock.instant().plus(Duration.ofMinutes(1)) : to;
        Instant start = from == null ? end.minus(Duration.ofDays(7)) : from;
        if (Duration.between(start, end).compareTo(MAX_RANGE) > 0) {
            throw ApiException.rule("The date range can be at most one year.");
        }
        Long effectiveCashier = user.has("SALE_VIEW_ALL") ? cashierId : user.id();
        var pageable = PageRequests.of(page, size, sort, SORTS, Sort.by(Sort.Direction.DESC, "completedAt"));
        String receiptFilter = receipt == null || receipt.isBlank() ? null : receipt.trim();
        Page<Sale> result = sales.search(storeId, start, end, effectiveCashier, registerId, customerId, status, channel,
                receiptFilter, pageable);
        List<Sale> content = result.getContent();
        Map<Long, String> names = users.displayNames(content.stream().map(Sale::getCashierId).collect(Collectors.toSet()));
        Map<Long, String> codes = stores.registerCodes(content.stream().map(Sale::getRegisterId).collect(Collectors.toSet()));
        Map<Long, String> customerNames = customers.findAllById(content.stream().map(Sale::getCustomerId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getFullName));
        Map<Long, List<Payment>> pays = payments.forSales(content.stream().map(Sale::getId).toList());
        return PageResponse.of(result, s -> new SaleSummaryDto(s.getId(), s.getReceiptNumber(), s.getStatus(),
                s.getChannel(), s.getCompletedAt(), names.get(s.getCashierId()), codes.get(s.getRegisterId()),
                customerNames.get(s.getCustomerId()), s.getGrandTotal(), s.getRefundedTotal(), s.getItemCount(),
                pays.getOrDefault(s.getId(), List.of()).stream().map(Payment::getTenderType).distinct().toList()));
    }

    /** Replay helper for idempotent checkout (no permission re-check: the original call was authorized). */
    @Transactional(readOnly = true)
    public SaleDto loadDto(Long saleId) {
        return toDto(sales.findWithItemsById(saleId).orElseThrow(() -> ApiException.notFound("Sale", saleId)), false);
    }

    @Transactional(readOnly = true)
    public SaleDto toDto(Sale s, boolean reprint) {
        Store store = stores.requireStore(s.getStoreId());
        String cashier = users.displayNames(List.of(s.getCashierId())).get(s.getCashierId());
        String register = stores.registerCodes(List.of(s.getRegisterId())).get(s.getRegisterId());
        String customerName = s.getCustomerId() == null ? null
                : customers.findById(s.getCustomerId()).map(Customer::getFullName).orElse(null);
        List<LineDto> lines = s.getItems().stream().map(SaleQueryService::toLine).toList();
        TotalsDto totals = new TotalsDto(s.getSubtotal(), s.getLineDiscountTotal(), s.getTransactionDiscountTotal(),
                s.getDiscountTotal(), s.getTaxTotal(), s.getGrandTotal(), s.getItemCount());
        OnlineOrderNumbers resolver = orderNumbers.getIfAvailable();
        String orderNumber = s.getOnlineOrderId() == null || resolver == null ? null
                : resolver.orderNumber(s.getOnlineOrderId());
        return new SaleDto(s.getId(), s.getReceiptNumber(), s.getStatus(), s.getChannel(), s.getCompletedAt(),
                new StoreInfo(store.getName(), store.getAddressLine1(), store.getAddressLine2(), store.getCity(),
                        store.getPhone(), store.getTaxRegistrationNo(), store.getReceiptHeader(), store.getReceiptFooter()),
                cashier, register, s.getCustomerId(), customerName, s.getCurrency(), s.isPricesIncludeTax(), lines, totals,
                s.getAmountPaid(), s.getAmountTendered(), s.getChangeDue(), payments.forSale(s.getId()),
                s.getLoyaltyPointsEarned(), s.getLoyaltyPointsRedeemed(), s.getRefundedTotal(), orderNumber, reprint,
                s.getReprintCount());
    }

    // ----- ShiftActivity -----------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Contribution contributionFor(Long shiftId) {
        Map<String, BigDecimal> tenderAmounts = new HashMap<>();
        Map<String, Long> tenderCounts = new HashMap<>();
        for (Object[] row : sales.tenderTotalsForShift(shiftId)) {
            tenderAmounts.put((String) row[0], (BigDecimal) row[2]);
            tenderCounts.put((String) row[0], (Long) row[1]);
        }
        Object[] totals = sales.shiftTotals(shiftId).get(0);
        long count = (Long) totals[0];
        BigDecimal total = (BigDecimal) totals[1];
        return new Contribution(tenderAmounts.getOrDefault("CASH", BigDecimal.ZERO), BigDecimal.ZERO, tenderAmounts,
                tenderCounts, Map.of("sales", count), Map.of("salesTotal", MoneyPolicy.amount(total)));
    }

    private static void checkAccess(Sale sale, CurrentUser user) {
        user.assertStoreAccess(sale.getStoreId(), "Sale", sale.getId());
        if (!user.has("SALE_VIEW_ALL") && !(user.has("SALE_VIEW_OWN") && sale.getCashierId().equals(user.id()))) {
            throw ApiException.notFound("Sale", sale.getId());
        }
    }

    static LineDto toLine(SaleItem i) {
        return new LineDto(i.getLineNumber(), i.getProductId(), i.getProductNameSnapshot(), i.getSkuSnapshot(),
                i.getBarcodeSnapshot(), i.getUnitOfMeasure(), i.getQuantity(), i.getUnitPrice(), i.getOriginalUnitPrice(),
                i.getGrossAmount(), i.getPromoDiscountAmount(), null, i.getManualDiscountAmount(),
                i.getAllocatedTxnDiscountAmount(), i.getNetAmount(), i.getTaxRatePercent(), i.getTaxAmount(),
                i.getLineTotal(), null, true, i.getQuantityReturned());
    }
}
