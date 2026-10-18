package com.supermarket.pos.sales.service;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.products.entity.Product;
import com.supermarket.pos.products.entity.ProductBarcode;
import com.supermarket.pos.products.entity.ProductPromotion;
import com.supermarket.pos.products.service.ProductService;
import com.supermarket.pos.products.service.QuantityRules;
import com.supermarket.pos.sales.dto.SalesDtos.DiscountRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prices a basket. Used by the till quote, checkout and online orders, so every channel
 * computes totals the same way. Per line (all values rounded to 2 dp, HALF_UP):
 * <pre>
 *   gross     = unit price × quantity               (override price if authorized)
 *   promo     = best running promotion × quantity   (not combined with a price override)
 *   manual    = authorized line discount (on gross − promo)
 *   allocated = share of the transaction discount  (largest-remainder allocation)
 *   net       = gross − promo − manual − allocated
 *   tax       = net × r / (100 + r) when prices include tax, else net × r / 100
 *   line total = net (+ tax when prices exclude tax)
 * </pre>
 * Header totals are sums of rounded line values, so lines always add up to the receipt total.
 */
@Component
public class PricingEngine {

    private final ProductService products;
    private final Clock clock;

    public PricingEngine(ProductService products, Clock clock) {
        this.products = products;
        this.clock = clock;
    }

    public record LineInput(Long productId, BigDecimal quantity, DiscountRequest manualDiscount, BigDecimal overridePrice) {
    }

    public record PricedLine(int lineNumber, Product product, String barcode, BigDecimal quantity, BigDecimal unitPrice,
                             BigDecimal originalUnitPrice, boolean overridden, BigDecimal gross, BigDecimal promo,
                             ProductPromotion promotion, BigDecimal manual, DiscountRequest manualRequest,
                             BigDecimal allocated, BigDecimal net, BigDecimal taxRate, BigDecimal tax, BigDecimal lineTotal) {
    }

    public record PricedCart(List<PricedLine> lines, BigDecimal subtotal, BigDecimal lineDiscountTotal,
                             BigDecimal transactionDiscountTotal, BigDecimal discountTotal, BigDecimal taxTotal,
                             BigDecimal grandTotal, int itemCount, BigDecimal maxManualDiscountPercent,
                             boolean hasManualDiscount, boolean hasPriceOverride, boolean pricesIncludeTax) {
    }

    @Transactional(readOnly = true)
    public PricedCart price(List<LineInput> inputs, DiscountRequest transactionDiscount, boolean pricesIncludeTax) {
        Instant now = clock.instant();
        Map<Long, Product> productMap = products.loadForPricing(inputs.stream().map(LineInput::productId).toList());
        Map<Long, ProductPromotion> promos = products.bestPromotions(productMap.values(), now);

        List<PricedLine> stage = new ArrayList<>();
        BigDecimal maxPercent = BigDecimal.ZERO;
        boolean anyManual = false;
        boolean anyOverride = false;
        int lineNumber = 1;
        for (LineInput in : inputs) {
            Product p = productMap.get(in.productId());
            if (p == null) {
                throw ApiException.notFound("Product", in.productId());
            }
            if (!p.isActive()) {
                throw new ApiException(ErrorCode.PRODUCT_INACTIVE, p.getName() + " is not for sale (inactive).",
                        Map.of("productId", p.getId()));
            }
            BigDecimal qty = QuantityRules.validate(p, in.quantity());
            BigDecimal original = p.getSellingPrice();
            boolean overridden = in.overridePrice() != null && in.overridePrice().compareTo(original) != 0;
            if (in.overridePrice() != null && !p.isAllowPriceOverride()) {
                throw ApiException.rule("The price of " + p.getName() + " cannot be overridden.");
            }
            BigDecimal unitPrice = overridden ? MoneyPolicy.amount(in.overridePrice()) : original;
            BigDecimal gross = MoneyPolicy.extend(unitPrice, qty);

            ProductPromotion promotion = overridden ? null : promos.get(p.getId());
            BigDecimal promo = promotion == null ? MoneyPolicy.ZERO
                    : MoneyPolicy.amount(ProductService.perUnitDiscount(promotion, unitPrice).multiply(qty)).min(gross);

            BigDecimal afterPromo = gross.subtract(promo);
            BigDecimal manual = MoneyPolicy.ZERO;
            if (in.manualDiscount() != null && in.manualDiscount().value().signum() > 0) {
                manual = discountAmount(in.manualDiscount(), afterPromo, p.getName());
                anyManual = true;
                maxPercent = maxPercent.max(percent(manual, afterPromo));
            }
            anyOverride |= overridden;
            String barcode = p.primaryBarcode().map(ProductBarcode::getBarcode).orElse(null);
            stage.add(new PricedLine(lineNumber++, p, barcode, qty, unitPrice, original, overridden, gross, promo, promotion,
                    manual, in.manualDiscount(), null, afterPromo.subtract(manual), p.getTaxRate().getRatePercent(),
                    null, null));
        }

        // Transaction discount, spread over lines in proportion to their remaining value.
        BigDecimal beforeTxn = stage.stream().map(PricedLine::net).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal txn = MoneyPolicy.ZERO;
        if (transactionDiscount != null && transactionDiscount.value().signum() > 0) {
            txn = discountAmount(transactionDiscount, beforeTxn, "the basket");
            anyManual = true;
            maxPercent = maxPercent.max(percent(txn, beforeTxn));
        }
        List<BigDecimal> shares = MoneyPolicy.allocate(txn, stage.stream().map(PricedLine::net).toList());

        List<PricedLine> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal lineDiscounts = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal grand = BigDecimal.ZERO;
        int items = 0;
        for (int i = 0; i < stage.size(); i++) {
            PricedLine s = stage.get(i);
            BigDecimal allocated = shares.get(i);
            BigDecimal net = s.net().subtract(allocated);
            BigDecimal tax = pricesIncludeTax ? MoneyPolicy.taxIncluded(net, s.taxRate()) : MoneyPolicy.taxExclusive(net, s.taxRate());
            BigDecimal lineTotal = pricesIncludeTax ? net : net.add(tax);
            lines.add(new PricedLine(s.lineNumber(), s.product(), s.barcode(), s.quantity(), s.unitPrice(),
                    s.originalUnitPrice(), s.overridden(), s.gross(), s.promo(), s.promotion(), s.manual(),
                    s.manualRequest(), allocated, net, s.taxRate(), tax, lineTotal));
            subtotal = subtotal.add(s.gross());
            lineDiscounts = lineDiscounts.add(s.promo()).add(s.manual());
            taxTotal = taxTotal.add(tax);
            grand = grand.add(lineTotal);
            items += s.product().getUnitOfMeasure().isFractional() ? 1 : s.quantity().intValue();
        }
        return new PricedCart(lines, MoneyPolicy.amount(subtotal), MoneyPolicy.amount(lineDiscounts), MoneyPolicy.amount(txn),
                MoneyPolicy.amount(lineDiscounts.add(txn)), MoneyPolicy.amount(taxTotal), MoneyPolicy.amount(grand), items,
                maxPercent.setScale(2, RoundingMode.CEILING), anyManual, anyOverride, pricesIncludeTax);
    }

    private static BigDecimal discountAmount(DiscountRequest d, BigDecimal base, String what) {
        if ("PERCENT".equals(d.method())) {
            if (d.value().compareTo(MoneyPolicy.HUNDRED) > 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "A discount cannot exceed 100%.");
            }
            return MoneyPolicy.percentOf(base, d.value());
        }
        BigDecimal amount = MoneyPolicy.amount(d.value());
        if (amount.compareTo(base) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The discount on " + what + " is more than its price.");
        }
        return amount;
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return part.signum() == 0 ? BigDecimal.ZERO : MoneyPolicy.HUNDRED;
        }
        return part.multiply(MoneyPolicy.HUNDRED).divide(whole, 4, RoundingMode.HALF_UP);
    }
}
