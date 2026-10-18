package com.supermarket.pos.inventory.service;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.inventory.entity.InventoryBalance;
import com.supermarket.pos.inventory.entity.InventoryBatch;
import com.supermarket.pos.inventory.entity.MovementType;
import com.supermarket.pos.inventory.entity.StockMovement;
import com.supermarket.pos.inventory.event.StockLevelsChanged;
import com.supermarket.pos.inventory.repository.InventoryBalanceRepository;
import com.supermarket.pos.inventory.repository.InventoryBatchRepository;
import com.supermarket.pos.inventory.repository.StockMovementRepository;
import com.supermarket.pos.products.entity.Product;
import com.supermarket.pos.products.service.ProductService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The inventory ledger. All stock changes in the system go through {@link #deduct} or
 * {@link #receive}, which must run inside the caller's business transaction:
 * <ol>
 *   <li>balance rows are created if missing and locked FOR UPDATE in ascending product id order;</li>
 *   <li>availability is checked against the locked quantities (no overselling, no negative stock);</li>
 *   <li>batch-tracked products are allocated first-expiry-first-out (expired batches last);</li>
 *   <li>one immutable movement per batch portion is written with before/after quantities.</li>
 * </ol>
 * A database CHECK (quantity_on_hand >= 0) and a unique key per document line back these rules up.
 */
@Service
public class InventoryService {

    private final InventoryBalanceRepository balances;
    private final InventoryBatchRepository batches;
    private final StockMovementRepository movements;
    private final ProductService products;
    private final ApplicationEventPublisher events;
    private final BalanceInitializer initializer;
    private final Clock clock;

    public InventoryService(InventoryBalanceRepository balances, InventoryBatchRepository batches,
                            StockMovementRepository movements, ProductService products,
                            ApplicationEventPublisher events, BalanceInitializer initializer, Clock clock) {
        this.balances = balances;
        this.batches = batches;
        this.movements = movements;
        this.products = products;
        this.events = events;
        this.initializer = initializer;
        this.clock = clock;
    }

    /** Stock leaving the store. {@code batchId} pins a specific batch (e.g. a write-off of one lot). */
    public record Deduction(Long productId, BigDecimal quantity, Long lineId, Long batchId) {
        public Deduction(Long productId, BigDecimal quantity, Long lineId) {
            this(productId, quantity, lineId, null);
        }
    }

    public record BatchPortion(Long batchId, String batchNumber, LocalDate expiryDate, BigDecimal quantity,
                               BigDecimal unitCost) {
    }

    /** What a deduction consumed; {@code costAmount} is the cost of goods for margin reporting. */
    public record Allocation(Long productId, Long lineId, BigDecimal quantity, BigDecimal costAmount,
                             BigDecimal unitCost, List<BatchPortion> portions, boolean usedExpiredBatch) {
    }

    /**
     * Stock entering the store. {@code unitCost == null} means "at the current average cost"
     * (e.g. found stock in a count). {@code batchId} returns goods into an existing batch.
     */
    public record Receipt(Long productId, BigDecimal quantity, BigDecimal unitCost, String batchNumber,
                          LocalDate expiryDate, Long lineId, Long batchId, Long goodsReceiptItemId) {
    }

    public record ReceivedLine(Long productId, Long lineId, Long batchId, BigDecimal unitCost) {
    }

    public record Shortage(Long productId, String name, BigDecimal requested, BigDecimal available) {
    }

    /** Where a movement came from (sale, goods receipt, adjustment, ...). */
    public record Reference(String type, Long id, Long userId, String reason) {
    }

    // ----- reads ------------------------------------------------------------------------------

    /** Advisory availability check without locks (quotes). Returns only the short products. */
    @Transactional(readOnly = true)
    public Map<Long, Shortage> shortages(Long storeId, Map<Long, BigDecimal> requested, Map<Long, Product> productMap) {
        if (requested.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> onHand = balances.findByStoreIdAndProductIdIn(storeId, requested.keySet()).stream()
                .collect(Collectors.toMap(InventoryBalance::getProductId, InventoryBalance::getQuantityOnHand));
        Map<Long, Shortage> result = new LinkedHashMap<>();
        requested.forEach((productId, qty) -> {
            BigDecimal available = onHand.getOrDefault(productId, BigDecimal.ZERO);
            if (available.compareTo(qty) < 0) {
                Product p = productMap.get(productId);
                result.put(productId, new Shortage(productId, p == null ? null : p.getName(), qty, available));
            }
        });
        return result;
    }

    // ----- writes -----------------------------------------------------------------------------

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Allocation> deduct(Long storeId, MovementType type, Reference ref, List<Deduction> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> productMap = loadProducts(lines.stream().map(Deduction::productId).toList());
        Map<Long, InventoryBalance> locked = lockBalances(storeId, productMap.keySet());

        // Availability over the whole basket (the same product may appear on several lines).
        Map<Long, BigDecimal> requested = new LinkedHashMap<>();
        lines.forEach(l -> requested.merge(l.productId(), l.quantity(), BigDecimal::add));
        List<Shortage> shortages = new ArrayList<>();
        requested.forEach((productId, qty) -> {
            BigDecimal available = locked.get(productId).getQuantityOnHand();
            if (available.compareTo(qty) < 0) {
                shortages.add(new Shortage(productId, productMap.get(productId).getName(), qty, available));
            }
        });
        if (!shortages.isEmpty()) {
            throw insufficient(shortages);
        }

        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Map<Long, List<InventoryBatch>> fefoCache = new HashMap<>();
        List<StockMovement> written = new ArrayList<>();
        List<Allocation> allocations = new ArrayList<>();

        for (Deduction line : lines) {
            Product product = productMap.get(line.productId());
            InventoryBalance balance = locked.get(line.productId());
            List<BatchPortion> portions = new ArrayList<>();
            boolean usedExpired = false;
            BigDecimal remaining = line.quantity();

            if (line.batchId() != null) {
                InventoryBatch batch = batches.lockById(line.batchId())
                        .filter(b -> b.getStoreId().equals(storeId) && b.getProductId().equals(line.productId()))
                        .orElseThrow(() -> ApiException.notFound("Batch", line.batchId()));
                if (batch.getQuantityRemaining().compareTo(remaining) < 0) {
                    throw ApiException.rule("Batch " + batch.getBatchNumber() + " only has "
                            + batch.getQuantityRemaining().stripTrailingZeros().toPlainString() + " left.");
                }
                portions.add(take(batch, remaining));
                usedExpired = batch.isExpired(today);
                remaining = BigDecimal.ZERO;
            } else if (product.isTrackBatches()) {
                List<InventoryBatch> available = fefoCache.computeIfAbsent(line.productId(),
                        pid -> batches.lockAvailableFefo(storeId, pid));
                // Pass 1: in-date batches (FEFO). Pass 2: expired batches, flagged for follow-up.
                for (boolean expiredPass : new boolean[]{false, true}) {
                    for (InventoryBatch batch : available) {
                        if (remaining.signum() == 0) {
                            break;
                        }
                        if (batch.isExpired(today) != expiredPass || batch.getQuantityRemaining().signum() == 0) {
                            continue;
                        }
                        BigDecimal portion = batch.getQuantityRemaining().min(remaining);
                        portions.add(take(batch, portion));
                        remaining = remaining.subtract(portion);
                        usedExpired |= expiredPass;
                    }
                }
            }
            if (remaining.signum() > 0) {
                // Untracked stock (no batch) is valued at the moving average cost.
                portions.add(new BatchPortion(null, null, null, remaining, balance.getAverageCost()));
            }

            BigDecimal cost = BigDecimal.ZERO;
            for (BatchPortion portion : portions) {
                written.add(post(balance, type, portion.quantity().negate(), portion.unitCost(), portion.batchId(),
                        ref, line.lineId(), now));
                cost = cost.add(portion.quantity().multiply(portion.unitCost()));
            }
            BigDecimal costAmount = MoneyPolicy.amount(cost);
            BigDecimal unitCost = cost.divide(line.quantity(), MoneyPolicy.COST_SCALE, RoundingMode.HALF_UP);
            allocations.add(new Allocation(line.productId(), line.lineId(), line.quantity(), costAmount, unitCost,
                    List.copyOf(portions), usedExpired));
        }
        movements.saveAll(written);
        events.publishEvent(new StockLevelsChanged(storeId, Set.copyOf(productMap.keySet())));
        return allocations;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<ReceivedLine> receive(Long storeId, MovementType type, Reference ref, List<Receipt> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> productMap = loadProducts(lines.stream().map(Receipt::productId).toList());
        Map<Long, InventoryBalance> locked = lockBalances(storeId, productMap.keySet());
        Instant now = clock.instant();
        List<StockMovement> written = new ArrayList<>();
        List<ReceivedLine> result = new ArrayList<>();

        for (Receipt line : lines) {
            Product product = productMap.get(line.productId());
            InventoryBalance balance = locked.get(line.productId());
            if (line.quantity() == null || line.quantity().signum() <= 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Received quantity must be positive.");
            }
            BigDecimal unitCost = line.unitCost() == null ? balance.getAverageCost() : MoneyPolicy.cost(line.unitCost());
            if (unitCost.signum() < 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unit cost cannot be negative.");
            }
            balance.setAverageCost(movingAverage(balance.getQuantityOnHand(), balance.getAverageCost(),
                    line.quantity(), unitCost));

            Long batchId = null;
            if (line.batchId() != null) {
                InventoryBatch batch = batches.lockById(line.batchId())
                        .filter(b -> b.getStoreId().equals(storeId) && b.getProductId().equals(line.productId()))
                        .orElseThrow(() -> ApiException.notFound("Batch", line.batchId()));
                batch.setQuantityRemaining(batch.getQuantityRemaining().add(line.quantity()));
                batch.setStatus(InventoryBatch.Status.ACTIVE);
                batchId = batch.getId();
            } else if (product.isTrackBatches()) {
                batchId = receiveIntoBatch(storeId, product, line, unitCost, ref, now).getId();
            }
            written.add(post(balance, type, line.quantity(), unitCost, batchId, ref, line.lineId(), now));
            result.add(new ReceivedLine(line.productId(), line.lineId(), batchId, unitCost));
        }
        movements.saveAll(written);
        events.publishEvent(new StockLevelsChanged(storeId, Set.copyOf(productMap.keySet())));
        return result;
    }

    // ----- helpers ----------------------------------------------------------------------------

    private InventoryBatch receiveIntoBatch(Long storeId, Product product, Receipt line, BigDecimal unitCost,
                                            Reference ref, Instant now) {
        String number = line.batchNumber() == null || line.batchNumber().isBlank()
                ? ref.type() + "-" + ref.id() + "-" + line.lineId()
                : line.batchNumber().trim();
        if (product.isTrackExpiry() && line.expiryDate() == null
                && (ref.type().equals("GOODS_RECEIPT") || ref.type().equals("STOCK_ADJUSTMENT"))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, product.getName() + " requires an expiry date.",
                    Map.of("productId", product.getId()));
        }
        InventoryBatch batch = batches.findByStoreIdAndProductIdAndBatchNumber(storeId, product.getId(), number)
                .orElse(null);
        if (batch == null) {
            batch = new InventoryBatch();
            batch.setStoreId(storeId);
            batch.setProductId(product.getId());
            batch.setBatchNumber(number);
            batch.setExpiryDate(line.expiryDate());
            batch.setReceivedAt(now);
            batch.setUnitCost(unitCost);
            batch.setQuantityReceived(line.quantity());
            batch.setQuantityRemaining(line.quantity());
            batch.setGoodsReceiptItemId(line.goodsReceiptItemId());
            return batches.saveAndFlush(batch);
        }
        if (line.expiryDate() != null && batch.getExpiryDate() != null && !line.expiryDate().equals(batch.getExpiryDate())) {
            throw ApiException.rule("Batch " + number + " already exists with expiry " + batch.getExpiryDate()
                    + ". Use a different batch number.");
        }
        batch.setUnitCost(movingAverage(batch.getQuantityRemaining(), batch.getUnitCost(), line.quantity(), unitCost));
        batch.setQuantityReceived(batch.getQuantityReceived().add(line.quantity()));
        batch.setQuantityRemaining(batch.getQuantityRemaining().add(line.quantity()));
        batch.setStatus(InventoryBatch.Status.ACTIVE);
        return batch;
    }

    private static BatchPortion take(InventoryBatch batch, BigDecimal quantity) {
        batch.setQuantityRemaining(batch.getQuantityRemaining().subtract(quantity));
        if (batch.getQuantityRemaining().signum() == 0) {
            batch.setStatus(InventoryBatch.Status.DEPLETED);
        }
        return new BatchPortion(batch.getId(), batch.getBatchNumber(), batch.getExpiryDate(), quantity,
                batch.getUnitCost());
    }

    private StockMovement post(InventoryBalance balance, MovementType type, BigDecimal change, BigDecimal unitCost,
                               Long batchId, Reference ref, Long lineId, Instant now) {
        BigDecimal before = balance.getQuantityOnHand();
        BigDecimal after = MoneyPolicy.quantity(before.add(change));
        if (after.signum() < 0) {
            throw insufficient(List.of(new Shortage(balance.getProductId(), null, change.negate(), before)));
        }
        balance.setQuantityOnHand(after);
        balance.setLastMovementAt(now);
        StockMovement m = new StockMovement();
        m.setStoreId(balance.getStoreId());
        m.setProductId(balance.getProductId());
        m.setBatchId(batchId);
        m.setMovementType(type);
        m.setQuantityChange(MoneyPolicy.quantity(change));
        m.setQuantityBefore(before);
        m.setQuantityAfter(after);
        m.setUnitCost(unitCost == null ? null : MoneyPolicy.cost(unitCost));
        m.setReferenceType(ref.type());
        m.setReferenceId(ref.id());
        m.setReferenceLineId(lineId);
        m.setReason(ref.reason() == null ? null : ref.reason().length() > 255 ? ref.reason().substring(0, 255) : ref.reason());
        m.setPerformedBy(ref.userId());
        m.setCreatedAt(now);
        return m;
    }

    private Map<Long, InventoryBalance> lockBalances(Long storeId, Set<Long> productIds) {
        TreeSet<Long> sorted = new TreeSet<>(productIds);
        Set<Long> existing = new java.util.HashSet<>(balances.findExistingProductIds(storeId, sorted));
        if (existing.size() < sorted.size()) {
            Set<Long> missing = new TreeSet<>(sorted);
            missing.removeAll(existing);
            initializer.ensure(storeId, missing);
        }
        Map<Long, InventoryBalance> locked = balances.lockForUpdate(storeId, sorted).stream()
                .collect(Collectors.toMap(InventoryBalance::getProductId, Function.identity()));
        if (locked.size() != sorted.size()) {
            throw new IllegalStateException("Could not lock all inventory balances");
        }
        return locked;
    }

    private Map<Long, Product> loadProducts(List<Long> ids) {
        Map<Long, Product> map = products.loadForPricing(ids);
        for (Long id : ids) {
            if (!map.containsKey(id)) {
                throw ApiException.notFound("Product", id);
            }
        }
        return map;
    }

    /** (q0·c0 + q·c) / (q0 + q); a non-positive starting quantity takes the incoming cost. */
    static BigDecimal movingAverage(BigDecimal onHand, BigDecimal averageCost, BigDecimal quantity, BigDecimal unitCost) {
        if (onHand.signum() <= 0) {
            return MoneyPolicy.cost(unitCost);
        }
        BigDecimal total = onHand.multiply(averageCost).add(quantity.multiply(unitCost));
        return total.divide(onHand.add(quantity), MoneyPolicy.COST_SCALE, RoundingMode.HALF_UP);
    }

    private static ApiException insufficient(List<Shortage> shortages) {
        List<Map<String, Object>> items = shortages.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", s.productId());
            m.put("name", s.name());
            m.put("requested", s.requested().stripTrailingZeros().toPlainString());
            m.put("available", s.available().stripTrailingZeros().toPlainString());
            return m;
        }).toList();
        String first = shortages.get(0).name() != null ? shortages.get(0).name() : "a product";
        return new ApiException(ErrorCode.INSUFFICIENT_STOCK, shortages.size() == 1
                ? "Not enough stock for " + first + "."
                : "Not enough stock for " + shortages.size() + " items.", Map.of("items", items));
    }
}
