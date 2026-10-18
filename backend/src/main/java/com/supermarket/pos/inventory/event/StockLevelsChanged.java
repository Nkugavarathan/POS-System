package com.supermarket.pos.inventory.event;

import java.util.Set;

/** Published inside the transaction that changed stock; consumed after commit (alerts). */
public record StockLevelsChanged(Long storeId, Set<Long> productIds) {
}
