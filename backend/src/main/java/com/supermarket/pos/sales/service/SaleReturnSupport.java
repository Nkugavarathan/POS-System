package com.supermarket.pos.sales.service;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.sales.entity.Sale;
import com.supermarket.pos.sales.entity.SaleItem;
import com.supermarket.pos.sales.repository.SaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * The only way other modules change a completed sale: recording returned quantities and refunded
 * amounts. The sale row is locked so concurrent returns of the same receipt are serialized.
 */
@Service
public class SaleReturnSupport {

    private final SaleRepository sales;

    public SaleReturnSupport(SaleRepository sales) {
        this.sales = sales;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Sale lock(Long saleId) {
        Sale sale = sales.lockById(saleId).orElseThrow(() -> ApiException.notFound("Sale", saleId));
        sale.getItems().size();
        return sale;
    }

    /** What returning {@code quantity} of the line refunds (proportional; the last unit takes the remainder). */
    public record LineRefund(BigDecimal amount, BigDecimal tax) {
    }

    public LineRefund refundFor(SaleItem item, BigDecimal quantity) {
        BigDecimal remainingQty = item.getQuantity().subtract(item.getQuantityReturned());
        if (quantity.compareTo(remainingQty) > 0) {
            throw new ApiException(ErrorCode.RETURN_QUANTITY_EXCEEDED, item.getProductNameSnapshot() + ": only "
                    + remainingQty.stripTrailingZeros().toPlainString() + " can still be returned.",
                    Map.of("saleItemId", item.getId(), "returnable", remainingQty.toPlainString()));
        }
        if (quantity.compareTo(remainingQty) == 0) {
            BigDecimal amount = item.getLineTotal().subtract(item.getRefundedAmount());
            BigDecimal taxRefunded = item.getQuantityReturned().signum() == 0 ? BigDecimal.ZERO
                    : MoneyPolicy.amount(item.getTaxAmount().multiply(item.getQuantityReturned())
                    .divide(item.getQuantity(), 8, RoundingMode.HALF_UP));
            return new LineRefund(amount, item.getTaxAmount().subtract(taxRefunded));
        }
        BigDecimal amount = MoneyPolicy.amount(item.getLineTotal().multiply(quantity).divide(item.getQuantity(), 8, RoundingMode.HALF_UP));
        BigDecimal tax = MoneyPolicy.amount(item.getTaxAmount().multiply(quantity).divide(item.getQuantity(), 8, RoundingMode.HALF_UP));
        return new LineRefund(amount, tax);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void applyReturn(Sale sale, SaleItem item, BigDecimal quantity, BigDecimal amount) {
        item.setQuantityReturned(item.getQuantityReturned().add(quantity));
        item.setRefundedAmount(item.getRefundedAmount().add(amount));
        sale.setRefundedTotal(sale.getRefundedTotal().add(amount));
        boolean all = sale.getItems().stream().allMatch(i -> i.getQuantityReturned().compareTo(i.getQuantity()) == 0);
        sale.setStatus(all ? Sale.Status.FULLY_RETURNED : Sale.Status.PARTIALLY_RETURNED);
    }
}
