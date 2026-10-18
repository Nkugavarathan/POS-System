package com.supermarket.pos.sales.service;

import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.common.security.CurrentUser;
import com.supermarket.pos.common.sequence.SequenceService;
import com.supermarket.pos.customers.entity.LoyaltyAccount;
import com.supermarket.pos.customers.entity.LoyaltyTransaction;
import com.supermarket.pos.customers.service.LoyaltyService;
import com.supermarket.pos.payments.dto.PaymentDtos.PaymentRequest;
import com.supermarket.pos.payments.service.PaymentProvider.PaymentContext;
import com.supermarket.pos.payments.service.PaymentProvider.PlannedPayment;
import com.supermarket.pos.payments.service.PaymentService;
import com.supermarket.pos.pos.entity.CashierShift;
import com.supermarket.pos.pos.service.ShiftService;
import com.supermarket.pos.sales.dto.SalesDtos.SaleDto;
import com.supermarket.pos.sales.entity.Sale;
import com.supermarket.pos.sales.entity.SaleItem;
import com.supermarket.pos.sales.repository.SaleRepository;
import com.supermarket.pos.store.entity.Store;
import com.supermarket.pos.store.service.StoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Records a sale whose lines were priced earlier, for example an online order collected at
 * the counter: the customer pays the locked (packed) prices and stock already left the shelf when
 * the order was packed, so it is not deducted again here. Payment, receipt number, shift and
 * loyalty rules are exactly those of a normal checkout.
 */
@Service
public class PrepricedSaleService {

    private final ShiftService shifts;
    private final PaymentService payments;
    private final LoyaltyService loyalty;
    private final StoreService stores;
    private final SequenceService sequences;
    private final SaleRepository sales;
    private final SaleQueryService queries;
    private final AuditService audit;
    private final Clock clock;

    public PrepricedSaleService(ShiftService shifts, PaymentService payments, LoyaltyService loyalty, StoreService stores,
                                SequenceService sequences, SaleRepository sales, SaleQueryService queries,
                                AuditService audit, Clock clock) {
        this.shifts = shifts;
        this.payments = payments;
        this.loyalty = loyalty;
        this.stores = stores;
        this.sequences = sequences;
        this.sales = sales;
        this.queries = queries;
        this.audit = audit;
        this.clock = clock;
    }

    public record PrepricedLine(Long productId, String sku, String barcode, String name, Long categoryId,
                                String unitOfMeasure, BigDecimal quantity, BigDecimal unitPrice, BigDecimal gross,
                                BigDecimal promo, BigDecimal net, BigDecimal taxRate, BigDecimal tax, BigDecimal lineTotal,
                                BigDecimal unitCost, BigDecimal costAmount) {
    }

    public record PrepricedSale(Long registerId, Long customerId, Long onlineOrderId, boolean pricesIncludeTax,
                                List<PrepricedLine> lines, List<PaymentRequest> payments, BigDecimal expectedTotal) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SaleDto record(PrepricedSale request, CurrentUser user) {
        Long storeId = user.requireActiveStore();
        CashierShift shift = shifts.lockOpenShift(user.id(), storeId, request.registerId());
        Store store = stores.requireStore(storeId);
        if (request.lines().isEmpty()) {
            throw ApiException.rule("There is nothing to charge.");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal promo = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        int items = 0;
        for (PrepricedLine l : request.lines()) {
            subtotal = subtotal.add(l.gross());
            promo = promo.add(l.promo());
            tax = tax.add(l.tax());
            total = total.add(l.lineTotal());
            cost = cost.add(l.costAmount() == null ? BigDecimal.ZERO : l.costAmount());
            items += l.quantity().stripTrailingZeros().scale() > 0 ? 1 : l.quantity().intValue();
        }
        total = MoneyPolicy.amount(total);
        if (request.expectedTotal() != null && total.compareTo(MoneyPolicy.amount(request.expectedTotal())) != 0) {
            throw new ApiException(ErrorCode.QUOTE_MISMATCH, "The amount to collect is " + total + ".",
                    Map.of("grandTotal", total.toPlainString()));
        }

        var rules = loyalty.rules(storeId);
        Optional<LoyaltyAccount> account = loyalty.find(request.customerId());
        List<PlannedPayment> planned = payments.plan(total, request.payments(), new PaymentContext(storeId,
                request.customerId(), account.map(LoyaltyAccount::getPointsBalance).orElse(0L), rules.pointValue(),
                rules.minRedeemPoints()));

        Instant now = clock.instant();
        Sale sale = new Sale();
        sale.setStoreId(storeId);
        sale.setRegisterId(shift.getRegisterId());
        sale.setShiftId(shift.getId());
        sale.setCashierId(user.id());
        sale.setCustomerId(request.customerId());
        sale.setChannel(request.onlineOrderId() == null ? Sale.Channel.POS : Sale.Channel.ONLINE);
        sale.setOnlineOrderId(request.onlineOrderId());
        sale.setCurrency(store.getCurrency());
        sale.setPricesIncludeTax(request.pricesIncludeTax());
        sale.setSubtotal(MoneyPolicy.amount(subtotal));
        sale.setLineDiscountTotal(MoneyPolicy.amount(promo));
        sale.setTransactionDiscountTotal(MoneyPolicy.ZERO);
        sale.setDiscountTotal(MoneyPolicy.amount(promo));
        sale.setTaxTotal(MoneyPolicy.amount(tax));
        sale.setGrandTotal(total);
        sale.setAmountPaid(total);
        sale.setAmountTendered(MoneyPolicy.amount(planned.stream().map(PlannedPayment::amountTendered).reduce(BigDecimal.ZERO, BigDecimal::add)));
        sale.setChangeDue(MoneyPolicy.amount(planned.stream().map(PlannedPayment::changeGiven).reduce(BigDecimal.ZERO, BigDecimal::add)));
        sale.setItemCount(items);
        sale.setCostTotal(MoneyPolicy.amount(cost));
        sale.setCompletedAt(now);
        String registerCode = stores.registerCodes(List.of(shift.getRegisterId())).get(shift.getRegisterId());
        sale.setReceiptNumber(sequences.nextDailyNumber("RECEIPT", store.getCode() + "-" + registerCode,
                stores.businessDate(store, now), 6));
        int lineNumber = 1;
        for (PrepricedLine l : request.lines()) {
            SaleItem i = new SaleItem();
            i.setSale(sale);
            i.setLineNumber(lineNumber++);
            i.setProductId(l.productId());
            i.setSkuSnapshot(l.sku());
            i.setBarcodeSnapshot(l.barcode());
            i.setProductNameSnapshot(l.name());
            i.setCategoryId(l.categoryId());
            i.setUnitOfMeasure(l.unitOfMeasure());
            i.setQuantity(l.quantity());
            i.setUnitPrice(l.unitPrice());
            i.setOriginalUnitPrice(l.unitPrice());
            i.setGrossAmount(l.gross());
            i.setPromoDiscountAmount(l.promo());
            i.setManualDiscountAmount(MoneyPolicy.ZERO);
            i.setAllocatedTxnDiscountAmount(MoneyPolicy.ZERO);
            i.setNetAmount(l.net());
            i.setTaxRatePercent(l.taxRate());
            i.setTaxAmount(l.tax());
            i.setLineTotal(l.lineTotal());
            i.setUnitCost(l.unitCost() == null ? BigDecimal.ZERO : l.unitCost());
            i.setCostAmount(l.costAmount() == null ? MoneyPolicy.ZERO : l.costAmount());
            sale.getItems().add(i);
        }
        Sale saved = sales.saveAndFlush(sale);
        payments.record(saved.getId(), planned, user.id());

        if (account.isPresent()) {
            long redeemed = planned.stream().filter(p -> p.loyaltyPoints() != null).mapToLong(PlannedPayment::loyaltyPoints).sum();
            BigDecimal paidByPoints = planned.stream().filter(p -> "LOYALTY".equals(p.tenderType()))
                    .map(PlannedPayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (redeemed > 0) {
                loyalty.post(saved.getCustomerId(), LoyaltyTransaction.Type.REDEEM, -redeemed, "SALE", saved.getId(),
                        "Paid " + paidByPoints + " on " + saved.getReceiptNumber(), user.id());
                saved.setLoyaltyPointsRedeemed(redeemed);
            }
            long earned = LoyaltyService.pointsFor(rules, total.subtract(paidByPoints));
            if (earned > 0) {
                loyalty.post(saved.getCustomerId(), LoyaltyTransaction.Type.EARN, earned, "SALE", saved.getId(),
                        saved.getReceiptNumber(), user.id());
                saved.setLoyaltyPointsEarned(earned);
            }
        }
        audit.record(AuditService.Entry.success("SALE_COMPLETED", "Sale", saved.getId(), saved.getReceiptNumber()
                + (request.onlineOrderId() != null ? " (online order collected)" : "")).withStore(storeId)
                .withDetails(Map.of("grandTotal", total.toPlainString(), "channel", saved.getChannel().name())));
        return queries.toDto(saved, false);
    }

    /** Buying rhythm of a customer (POS and online purchases alike). */
    public record ProductHistory(Long productId, long purchases, Instant first, Instant last) {
    }

    @Transactional(readOnly = true)
    public List<ProductHistory> productHistory(Long customerId) {
        return sales.productHistory(customerId).stream()
                .map(r -> new ProductHistory((Long) r[0], (Long) r[1], (Instant) r[2], (Instant) r[3]))
                .toList();
    }
}
