package com.supermarket.pos.products.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Read-only stock information for product screens. Declared by the products module and
 * implemented by the inventory module (which owns the ledger), so products never depends
 * on inventory.
 */
public interface StockLookup {

    record StockLevel(BigDecimal quantityOnHand, BigDecimal averageCost) {
    }

    /** product id → stock in the store; products without a ledger row are absent (= zero). */
    Map<Long, StockLevel> levels(Long storeId, Collection<Long> productIds);

    /** Active products at or below their reorder level in the store (including never-stocked ones). */
    Set<Long> lowStockProductIds(Long storeId);
}
