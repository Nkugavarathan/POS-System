package com.supermarket.pos.inventory.service;

import com.supermarket.pos.inventory.repository.InventoryBalanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.TreeSet;

/**
 * Creates missing zero-stock balance rows in a separate, immediately committed transaction.
 *
 * <p>Why separate: {@code INSERT IGNORE} on an existing row takes a shared lock. If that
 * happened inside the business transaction, two concurrent sales would each hold a shared
 * lock and then both wait for the exclusive {@code FOR UPDATE} lock: a deadlock. Committing
 * the (idempotent) inserts first means the business transaction only ever takes exclusive locks.
 */
@Component
public class BalanceInitializer {

    private final InventoryBalanceRepository balances;

    public BalanceInitializer(InventoryBalanceRepository balances) {
        this.balances = balances;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensure(Long storeId, Collection<Long> productIds) {
        for (Long productId : new TreeSet<>(productIds)) {
            balances.ensureExists(storeId, productId);
        }
    }
}
