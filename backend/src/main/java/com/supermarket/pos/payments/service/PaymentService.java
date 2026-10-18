package com.supermarket.pos.payments.service;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.payments.dto.PaymentDtos.PaymentDto;
import com.supermarket.pos.payments.dto.PaymentDtos.PaymentRequest;
import com.supermarket.pos.payments.entity.Payment;
import com.supermarket.pos.payments.repository.PaymentRepository;
import com.supermarket.pos.payments.service.PaymentProvider.PaymentContext;
import com.supermarket.pos.payments.service.PaymentProvider.PlannedPayment;
import com.supermarket.pos.store.entity.TenderType;
import com.supermarket.pos.store.service.StoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Splits the amount due across tenders. Rules: non-cash tenders are applied first, in the order
 * given, and may not exceed what is still due; at most one cash tender, which covers the
 * remainder and is the only source of change; the tenders must cover the total exactly.
 */
@Service
public class PaymentService {

    private final Map<String, PaymentProvider> providers;
    private final PaymentRepository payments;
    private final StoreService stores;
    private final Clock clock;

    public PaymentService(List<PaymentProvider> providers, PaymentRepository payments, StoreService stores, Clock clock) {
        this.providers = providers.stream().collect(Collectors.toMap(PaymentProvider::tenderType, Function.identity()));
        this.payments = payments;
        this.stores = stores;
        this.clock = clock;
    }

    public List<PlannedPayment> plan(BigDecimal total, List<PaymentRequest> requests, PaymentContext context) {
        if (requests == null || requests.isEmpty()) {
            throw new ApiException(ErrorCode.INSUFFICIENT_TENDER, "Add a payment.");
        }
        long cashCount = requests.stream().filter(r -> "CASH".equalsIgnoreCase(r.tenderType())).count();
        if (cashCount > 1) {
            throw new ApiException(ErrorCode.PAYMENT_INVALID, "Use a single cash line.");
        }
        List<PaymentRequest> ordered = new ArrayList<>(requests.stream()
                .filter(r -> !"CASH".equalsIgnoreCase(r.tenderType())).toList());
        requests.stream().filter(r -> "CASH".equalsIgnoreCase(r.tenderType())).forEach(ordered::add);

        BigDecimal remaining = MoneyPolicy.amount(total);
        List<PlannedPayment> planned = new ArrayList<>();
        for (PaymentRequest request : ordered) {
            String code = request.tenderType().trim().toUpperCase(Locale.ROOT);
            TenderType tender = stores.requireActiveTender(code);
            PaymentProvider provider = providers.get(tender.getCode());
            if (provider == null) {
                throw new ApiException(ErrorCode.PAYMENT_INVALID, "Tender " + code + " is not supported.");
            }
            if (remaining.signum() == 0) {
                throw new ApiException(ErrorCode.PAYMENT_INVALID, "The total is already covered; remove the extra payment.");
            }
            PlannedPayment p = provider.plan(request, remaining, context);
            planned.add(p);
            remaining = remaining.subtract(p.amount());
        }
        if (remaining.signum() > 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_TENDER, remaining + " is still due.",
                    Map.of("due", remaining.toPlainString()));
        }
        return planned;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Payment> record(Long saleId, List<PlannedPayment> planned, Long userId) {
        List<Payment> rows = planned.stream().map(p -> {
            Payment payment = new Payment();
            payment.setSaleId(saleId);
            payment.setTenderType(p.tenderType());
            payment.setAmount(p.amount());
            payment.setAmountTendered(p.amountTendered());
            payment.setChangeGiven(p.changeGiven());
            payment.setProvider(p.provider());
            payment.setProviderReference(p.reference());
            payment.setCardBrand(p.cardBrand());
            payment.setCardLast4(p.cardLast4());
            payment.setAuthCode(p.authCode());
            payment.setLoyaltyPoints(p.loyaltyPoints());
            payment.setRecordedBy(userId);
            payment.setCreatedAt(clock.instant());
            return payment;
        }).toList();
        return payments.saveAll(rows);
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> forSale(Long saleId) {
        return payments.findBySaleIdOrderByIdAsc(saleId).stream().map(PaymentService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Payment>> forSales(Collection<Long> saleIds) {
        if (saleIds.isEmpty()) {
            return Map.of();
        }
        return payments.findBySaleIdIn(saleIds).stream().collect(Collectors.groupingBy(Payment::getSaleId));
    }

    public static PaymentDto toDto(Payment p) {
        return new PaymentDto(p.getTenderType(), p.getAmount(), p.getAmountTendered(), p.getChangeGiven(),
                p.getCardBrand(), p.getCardLast4(), p.getAuthCode(), p.getLoyaltyPoints());
    }
}
