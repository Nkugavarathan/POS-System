package com.supermarket.pos.shop.service;

import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.inventory.entity.InventoryBatch;
import com.supermarket.pos.inventory.service.InventoryService;
import com.supermarket.pos.products.entity.ProductPromotion;
import com.supermarket.pos.products.service.ProductService;
import com.supermarket.pos.shop.dto.ShopDtos.RescueRunDto;
import com.supermarket.pos.store.entity.Store;
import com.supermarket.pos.store.service.SettingsService;
import com.supermarket.pos.store.service.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Rescue deals: batches close to their expiry date are discounted automatically, for that store
 * only, so they sell before they become waste. The deal ends when the batch expires or sells out.
 * Because sales allocate stock first-expiry-first-out, the discounted units are the ones leaving
 * the shelf first. The same price applies at the till and online (it is an ordinary promotion).
 */
@Service
public class RescueDealService {

    private static final Logger log = LoggerFactory.getLogger(RescueDealService.class);

    private final StoreService stores;
    private final SettingsService settings;
    private final InventoryService inventory;
    private final ProductService products;
    private final AuditService audit;
    private final Clock clock;

    public RescueDealService(StoreService stores, SettingsService settings, InventoryService inventory,
                             ProductService products, AuditService audit, Clock clock) {
        this.stores = stores;
        this.settings = settings;
        this.inventory = inventory;
        this.products = products;
        this.audit = audit;
        this.clock = clock;
    }

    /** Shortly after start-up, then hourly, so a restart never leaves deals stale for an hour. */
    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "PT1H")
    public void scheduled() {
        for (Store store : stores.activeStores()) {
            try {
                run(store.getId());
            } catch (RuntimeException e) {
                log.warn("Rescue deal run failed for store {}: {}", store.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public RescueRunDto run(Long storeId) {
        if (!settings.bool(storeId, "rescue.enabled", true)) {
            return new RescueRunDto(0, 0);
        }
        Store store = stores.requireStore(storeId);
        ZoneId zone = ZoneId.of(store.getTimezone());
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        int window = settings.integer(storeId, "rescue.daysBeforeExpiry", 3);
        BigDecimal percent = settings.decimal(storeId, "rescue.discountPercent", new BigDecimal("30"));
        BigDecimal lastDay = settings.decimal(storeId, "rescue.lastDayPercent", new BigDecimal("50"));

        int ended = 0;
        for (ProductPromotion promo : products.runningRescue(storeId, now)) {
            InventoryBatch batch = promo.getBatchId() == null ? null : inventory.batch(promo.getBatchId()).orElse(null);
            boolean soldOut = batch == null || batch.getQuantityRemaining().signum() == 0;
            long daysLeft = batch == null || batch.getExpiryDate() == null ? -1 : ChronoUnit.DAYS.between(today, batch.getExpiryDate());
            boolean needsDeeperCut = daysLeft <= 0 && promo.getDiscountValue().compareTo(lastDay) < 0;
            if (soldOut || needsDeeperCut) {
                products.endPromotion(promo.getId());
                ended++;
            }
        }

        int created = 0;
        List<InventoryBatch> soon = inventory.expiringBatches(storeId, today.plusDays(window));
        for (InventoryBatch b : soon) {
            if (b.getExpiryDate() == null || b.getExpiryDate().isBefore(today) || products.hasActiveRescue(b.getId())) {
                continue;
            }
            long daysLeft = ChronoUnit.DAYS.between(today, b.getExpiryDate());
            BigDecimal off = daysLeft <= 0 ? lastDay : percent;
            // ends at the start of the day after the expiry date, in the store's timezone
            Instant endsAt = b.getExpiryDate().plusDays(1).atStartOfDay(zone).toInstant();
            String label = daysLeft <= 0 ? "Rescue deal: last day" : "Rescue deal: " + daysLeft + " day" + (daysLeft == 1 ? "" : "s") + " left";
            products.createRescuePromotion(b.getProductId(), storeId, b.getId(), off, endsAt, label);
            created++;
        }
        if (created + ended > 0) {
            audit.record(AuditService.Entry.success("RESCUE_DEALS_UPDATED", "Store", storeId,
                    created + " rescue deals started, " + ended + " ended").withStore(storeId)
                    .withDetails(Map.of("percent", MoneyPolicy.amount(percent).toPlainString())));
        }
        return new RescueRunDto(created, ended);
    }
}
