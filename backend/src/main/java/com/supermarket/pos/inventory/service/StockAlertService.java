package com.supermarket.pos.inventory.service;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.common.web.PageRequests;
import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.inventory.dto.InventoryDtos.AlertDto;
import com.supermarket.pos.inventory.entity.InventoryBalance;
import com.supermarket.pos.inventory.entity.InventoryBatch;
import com.supermarket.pos.inventory.entity.StockAlert;
import com.supermarket.pos.inventory.event.StockLevelsChanged;
import com.supermarket.pos.inventory.repository.InventoryBalanceRepository;
import com.supermarket.pos.inventory.repository.InventoryBatchRepository;
import com.supermarket.pos.inventory.repository.StockAlertRepository;
import com.supermarket.pos.products.entity.Product;
import com.supermarket.pos.products.service.ProductService;
import com.supermarket.pos.store.entity.Store;
import com.supermarket.pos.store.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Low-stock and expiry alerts. Checks run after the stock-changing transaction commits
 * (asynchronously, so a sale never waits for them) plus a scheduled sweep for expiry dates.
 */
@Service
public class StockAlertService {

    private static final Logger log = LoggerFactory.getLogger(StockAlertService.class);
    private static final int EXPIRY_WARNING_DAYS = 30;

    private final StockAlertRepository alerts;
    private final InventoryBalanceRepository balances;
    private final InventoryBatchRepository batches;
    private final ProductService products;
    private final StoreRepository stores;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public StockAlertService(StockAlertRepository alerts, InventoryBalanceRepository balances,
                             InventoryBatchRepository batches, ProductService products, StoreRepository stores,
                             CurrentUserProvider currentUser, Clock clock) {
        this.alerts = alerts;
        this.balances = balances;
        this.batches = batches;
        this.products = products;
        this.stores = stores;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStockChanged(StockLevelsChanged event) {
        try {
            checkLowStock(event.storeId(), event.productIds());
        } catch (RuntimeException e) {
            // Alerts are advisory; never let them disturb the business flow.
            log.warn("Low-stock check failed for store {}: {}", event.storeId(), e.getMessage());
        }
    }

    void checkLowStock(Long storeId, Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return;
        }
        Map<Long, Product> productMap = products.loadForPricing(productIds);
        Map<Long, BigDecimal> onHand = balances.findByStoreIdAndProductIdIn(storeId, productIds).stream()
                .collect(Collectors.toMap(InventoryBalance::getProductId, InventoryBalance::getQuantityOnHand));
        Set<Long> alreadyOpen = new HashSet<>(alerts.findOpenProductIds(storeId, StockAlert.Type.LOW_STOCK, productIds));
        for (Product p : productMap.values()) {
            BigDecimal qty = onHand.getOrDefault(p.getId(), BigDecimal.ZERO);
            boolean low = p.isActive() && p.getReorderLevel().signum() > 0 && qty.compareTo(p.getReorderLevel()) <= 0;
            if (low && !alreadyOpen.contains(p.getId())) {
                alerts.save(alert(storeId, p.getId(), null, StockAlert.Type.LOW_STOCK,
                        p.getName() + " is low: " + qty.stripTrailingZeros().toPlainString() + " left (reorder at "
                                + p.getReorderLevel().stripTrailingZeros().toPlainString() + ")"));
            }
        }
    }

    /** Daily: batches expiring within 30 days, and batches already expired with stock left. */
    @Scheduled(cron = "0 5 2 * * *")
    @Transactional
    public void sweepExpiry() {
        for (Store store : stores.findAll()) {
            if (!store.isActive()) {
                continue;
            }
            LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(store.getTimezone())));
            List<InventoryBatch> soon = batches.findExpiring(store.getId(), today.plusDays(EXPIRY_WARNING_DAYS));
            if (soon.isEmpty()) {
                continue;
            }
            Map<Long, Product> productMap = products.loadForPricing(
                    soon.stream().map(InventoryBatch::getProductId).collect(Collectors.toSet()));
            Set<Long> ids = soon.stream().map(InventoryBatch::getId).collect(Collectors.toSet());
            Set<Long> warned = new HashSet<>(alerts.findBatchIdsWithAlert(store.getId(), StockAlert.Type.EXPIRING, ids));
            Set<Long> expiredAlerted = new HashSet<>(alerts.findBatchIdsWithAlert(store.getId(), StockAlert.Type.EXPIRED, ids));
            for (InventoryBatch b : soon) {
                Product p = productMap.get(b.getProductId());
                String name = p == null ? "Product " + b.getProductId() : p.getName();
                if (b.isExpired(today)) {
                    if (!expiredAlerted.contains(b.getId())) {
                        alerts.save(alert(store.getId(), b.getProductId(), b.getId(), StockAlert.Type.EXPIRED,
                                name + " batch " + b.getBatchNumber() + " expired on " + b.getExpiryDate()
                                        + "; write off the remaining " + b.getQuantityRemaining().stripTrailingZeros().toPlainString()));
                    }
                } else if (!warned.contains(b.getId())) {
                    long days = ChronoUnit.DAYS.between(today, b.getExpiryDate());
                    alerts.save(alert(store.getId(), b.getProductId(), b.getId(), StockAlert.Type.EXPIRING,
                            name + " batch " + b.getBatchNumber() + " expires in " + days + " day(s)"));
                }
            }
        }
    }

    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    @Transactional(readOnly = true)
    public PageResponse<AlertDto> open(Integer page, Integer size) {
        Long storeId = currentUser.require().requireActiveStore();
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "id"));
        var result = alerts.findByStoreIdAndAcknowledgedAtIsNull(storeId, pageable);
        Map<Long, Product> productMap = products.loadForPricing(
                result.getContent().stream().map(StockAlert::getProductId).collect(Collectors.toSet()));
        return PageResponse.of(result, a -> {
            Product p = productMap.get(a.getProductId());
            return new AlertDto(a.getId(), a.getProductId(), p == null ? null : p.getName(),
                    p == null ? null : p.getSku(), a.getBatchId(), a.getAlertType(), a.getMessage(), a.getCreatedAt());
        });
    }

    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    @Transactional
    public void acknowledge(Long id) {
        var user = currentUser.require();
        StockAlert alert = alerts.findById(id).orElseThrow(() -> ApiException.notFound("Alert", id));
        user.assertStoreAccess(alert.getStoreId(), "Alert", id);
        if (alert.getAcknowledgedAt() == null) {
            alert.setAcknowledgedAt(clock.instant());
            alert.setAcknowledgedBy(user.id());
        }
    }

    private StockAlert alert(Long storeId, Long productId, Long batchId, StockAlert.Type type, String message) {
        StockAlert a = new StockAlert();
        a.setStoreId(storeId);
        a.setProductId(productId);
        a.setBatchId(batchId);
        a.setAlertType(type);
        a.setMessage(message.length() > 255 ? message.substring(0, 255) : message);
        a.setCreatedAt(clock.instant());
        return a;
    }
}
