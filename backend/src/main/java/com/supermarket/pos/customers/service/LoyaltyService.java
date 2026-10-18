package com.supermarket.pos.customers.service;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.customers.entity.LoyaltyAccount;
import com.supermarket.pos.customers.entity.LoyaltyTransaction;
import com.supermarket.pos.customers.repository.LoyaltyAccountRepository;
import com.supermarket.pos.customers.repository.LoyaltyTransactionRepository;
import com.supermarket.pos.store.service.SettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

/**
 * Loyalty points. Rules are store settings: {@code loyalty.pointsPer100} points per 100 of
 * eligible spend (rounded down), {@code loyalty.pointValue} currency per redeemed point,
 * {@code loyalty.minRedeemPoints}. The account row is locked for every change and each change is
 * a ledger row, so the cached balance can always be re-derived.
 */
@Service
public class LoyaltyService {

    private final LoyaltyAccountRepository accounts;
    private final LoyaltyTransactionRepository transactions;
    private final SettingsService settings;
    private final Clock clock;

    public LoyaltyService(LoyaltyAccountRepository accounts, LoyaltyTransactionRepository transactions,
                          SettingsService settings, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.settings = settings;
        this.clock = clock;
    }

    public record Rules(boolean enabled, BigDecimal pointsPer100, BigDecimal pointValue, long minRedeemPoints) {
    }

    @Transactional(readOnly = true)
    public Rules rules(Long storeId) {
        return new Rules(settings.bool(storeId, "loyalty.enabled", true),
                settings.decimal(storeId, "loyalty.pointsPer100", BigDecimal.ONE),
                settings.decimal(storeId, "loyalty.pointValue", BigDecimal.ONE),
                settings.integer(storeId, "loyalty.minRedeemPoints", 100));
    }

    public static long pointsFor(Rules rules, BigDecimal eligibleAmount) {
        if (!rules.enabled() || eligibleAmount.signum() <= 0) {
            return 0;
        }
        return eligibleAmount.multiply(rules.pointsPer100()).divide(MoneyPolicy.HUNDRED, 0, RoundingMode.DOWN).longValueExact();
    }

    public static BigDecimal valueOf(Rules rules, long points) {
        return MoneyPolicy.amount(rules.pointValue().multiply(BigDecimal.valueOf(points)));
    }

    /** Points needed to pay {@code amount} (rounded up so the value never exceeds the amount covered). */
    public static long pointsToCover(Rules rules, BigDecimal amount) {
        if (rules.pointValue().signum() <= 0) {
            return 0;
        }
        return amount.divide(rules.pointValue(), 0, RoundingMode.CEILING).longValueExact();
    }

    @Transactional(readOnly = true)
    public Optional<LoyaltyAccount> find(Long customerId) {
        return customerId == null ? Optional.empty() : accounts.findByCustomerId(customerId);
    }

    /** Adds (positive) or removes (negative) points with a ledger entry. Inside the business transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public LoyaltyTransaction post(Long customerId, LoyaltyTransaction.Type type, long points, String refType, Long refId,
                                   String reason, Long userId) {
        if (points == 0) {
            return null;
        }
        LoyaltyAccount account = accounts.lockByCustomerId(customerId)
                .orElseThrow(() -> ApiException.rule("This customer has no loyalty account."));
        if (account.getStatus() != LoyaltyAccount.Status.ACTIVE && points < 0 && type == LoyaltyTransaction.Type.REDEEM) {
            throw ApiException.rule("This loyalty account is blocked.");
        }
        long newBalance = account.getPointsBalance() + points;
        if (newBalance < 0) {
            if (type == LoyaltyTransaction.Type.EARN_REVERSAL) {
                // Points earned on the returned goods were already spent: take what is there.
                points = -account.getPointsBalance();
                newBalance = 0;
                if (points == 0) {
                    return null;
                }
            } else {
                throw new ApiException(ErrorCode.PAYMENT_INVALID, "Not enough loyalty points (balance "
                        + account.getPointsBalance() + ").", Map.of("balance", account.getPointsBalance()));
            }
        }
        account.setPointsBalance(newBalance);
        LoyaltyTransaction t = new LoyaltyTransaction();
        t.setLoyaltyAccountId(account.getId());
        t.setTransactionType(type);
        t.setPoints(points);
        t.setBalanceAfter(newBalance);
        t.setReferenceType(refType);
        t.setReferenceId(refId);
        t.setReason(reason);
        t.setCreatedBy(userId);
        t.setCreatedAt(clock.instant());
        return transactions.save(t);
    }
}
