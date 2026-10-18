package com.supermarket.pos.sales.event;

import java.math.BigDecimal;

/** Published inside the checkout transaction; listeners act after commit. */
public record SaleCompleted(Long saleId, Long storeId, BigDecimal grandTotal) {
}
