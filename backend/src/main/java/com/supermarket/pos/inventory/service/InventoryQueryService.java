package com.supermarket.pos.inventory.service;

import com.supermarket.pos.categories.service.CategoryService;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.common.security.CurrentUser;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.common.web.PageRequests;
import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.inventory.dto.InventoryDtos.BatchDto;
import com.supermarket.pos.inventory.dto.InventoryDtos.ExpiringBatchDto;
import com.supermarket.pos.inventory.dto.InventoryDtos.InventoryDetailDto;
import com.supermarket.pos.inventory.dto.InventoryDtos.InventoryRowDto;
import com.supermarket.pos.inventory.dto.InventoryDtos.MovementDto;
import com.supermarket.pos.inventory.entity.InventoryBalance;
import com.supermarket.pos.inventory.entity.InventoryBatch;
import com.supermarket.pos.inventory.entity.MovementType;
import com.supermarket.pos.inventory.entity.StockMovement;
import com.supermarket.pos.inventory.repository.InventoryBalanceRepository;
import com.supermarket.pos.inventory.repository.InventoryBatchRepository;
import com.supermarket.pos.inventory.repository.StockMovementRepository;
import com.supermarket.pos.products.entity.Product;
import com.supermarket.pos.products.service.ProductService;
import com.supermarket.pos.products.service.StockLookup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Read side of the inventory module, plus the {@link StockLookup} used by product screens. */
@Service
public class InventoryQueryService implements StockLookup {

    private static final Map<String, String> SORTS = Map.of("name", "name", "sku", "sku");
    private static final List<Long> NONE = List.of(-1L);
    static final Duration MAX_MOVEMENT_RANGE = Duration.ofDays(92);

    private final InventoryBalanceRepository balances;
    private final InventoryBatchRepository batches;
    private final StockMovementRepository movements;
    private final ProductService products;
    private final CategoryService categories;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public InventoryQueryService(InventoryBalanceRepository balances, InventoryBatchRepository batches,
                                 StockMovementRepository movements, ProductService products,
                                 CategoryService categories, CurrentUserProvider currentUser, Clock clock) {
        this.balances = balances;
        this.batches = batches;
        this.movements = movements;
        this.products = products;
        this.categories = categories;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ----- StockLookup -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Map<Long, StockLevel> levels(Long storeId, Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return balances.findByStoreIdAndProductIdIn(storeId, new HashSet<>(productIds)).stream()
                .collect(Collectors.toMap(InventoryBalance::getProductId,
                        b -> new StockLevel(b.getQuantityOnHand(), b.getAverageCost())));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> lowStockProductIds(Long storeId) {
        return new HashSet<>(balances.findLowStockProductIds(storeId));
    }

    // ----- screens -----------------------------------------------------------------------------

    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    @Transactional(readOnly = true)
    public PageResponse<InventoryRowDto> list(String q, Long categoryId, boolean lowStock, boolean inStockOnly,
                                              boolean includeInactive, Integer page, Integer size, List<String> sort) {
        CurrentUser user = currentUser.require();
        Long storeId = user.requireActiveStore();
        var pageable = PageRequests.of(page, size, sort, SORTS, Sort.by("name"));
        Collection<Long> categoryIds = categoryId == null ? NONE : categories.withDescendants(categoryId);
        String query = q == null || q.isBlank() ? null : q.trim();
        Page<Object[]> rows = balances.list(storeId, query, categoryId == null, categoryIds, includeInactive,
                lowStock, inStockOnly, pageable);
        boolean showCost = user.has("PRODUCT_VIEW_COST");
        return PageResponse.of(rows, r -> toRow((Product) r[0], (InventoryBalance) r[1], showCost));
    }

    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    @Transactional(readOnly = true)
    public InventoryDetailDto detail(Long productId) {
        CurrentUser user = currentUser.require();
        Long storeId = user.requireActiveStore();
        Product product = products.loadForPricing(List.of(productId)).get(productId);
        if (product == null) {
            throw ApiException.notFound("Product", productId);
        }
        InventoryBalance balance = balances.findByStoreIdAndProductIdIn(storeId, List.of(productId)).stream()
                .findFirst().orElse(null);
        boolean showCost = user.has("PRODUCT_VIEW_COST");
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        List<BatchDto> batchList = batches.findByStoreIdAndProductIdOrderByExpiryDateAscReceivedAtAsc(storeId, productId)
                .stream().map(b -> toBatch(b, today, showCost)).toList();
        List<MovementDto> recent = movements.findTop20ByStoreIdAndProductIdOrderByIdDesc(storeId, productId).stream()
                .map(m -> toMovement(m, product)).toList();
        return new InventoryDetailDto(toRow(product, balance, showCost), batchList, recent);
    }

    @PreAuthorize("hasAnyAuthority('INVENTORY_VIEW','REPORT_VIEW')")
    @Transactional(readOnly = true)
    public PageResponse<MovementDto> movements(Long productId, MovementType type, Instant from, Instant to,
                                               Integer page, Integer size) {
        Long storeId = currentUser.require().requireActiveStore();
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(Duration.ofDays(30)) : from;
        if (!start.isBefore(end)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "'from' must be before 'to'.");
        }
        if (Duration.between(start, end).compareTo(MAX_MOVEMENT_RANGE) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The date range can be at most 92 days.");
        }
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "id"));
        Page<StockMovement> result = movements.search(storeId, productId, type, start, end, pageable);
        Map<Long, Product> productMap = products.loadForPricing(
                result.getContent().stream().map(StockMovement::getProductId).collect(Collectors.toSet()));
        return PageResponse.of(result, m -> toMovement(m, productMap.get(m.getProductId())));
    }

    @PreAuthorize("hasAnyAuthority('INVENTORY_VIEW','DASHBOARD_VIEW')")
    @Transactional(readOnly = true)
    public List<ExpiringBatchDto> expiring(int days) {
        Long storeId = currentUser.require().requireActiveStore();
        int window = Math.max(0, Math.min(days, 365));
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        List<InventoryBatch> found = batches.findExpiring(storeId, today.plusDays(window));
        Map<Long, Product> productMap = products.loadForPricing(
                found.stream().map(InventoryBatch::getProductId).collect(Collectors.toSet()));
        return found.stream().map(b -> {
            Product p = productMap.get(b.getProductId());
            return new ExpiringBatchDto(b.getId(), b.getProductId(), p == null ? null : p.getName(),
                    p == null ? null : p.getSku(), b.getBatchNumber(), b.getExpiryDate(),
                    ChronoUnit.DAYS.between(today, b.getExpiryDate()), b.getQuantityRemaining(),
                    MoneyPolicy.amount(b.getQuantityRemaining().multiply(b.getUnitCost())));
        }).toList();
    }

    // ----- mapping -----------------------------------------------------------------------------

    static InventoryRowDto toRow(Product p, InventoryBalance b, boolean showCost) {
        BigDecimal qty = b == null ? BigDecimal.ZERO.setScale(3) : b.getQuantityOnHand();
        BigDecimal avg = b == null ? BigDecimal.ZERO.setScale(4) : b.getAverageCost();
        boolean low = p.getReorderLevel().signum() > 0 && qty.compareTo(p.getReorderLevel()) <= 0;
        return new InventoryRowDto(p.getId(), p.getSku(), p.getName(), p.getCategory().getName(),
                p.getUnitOfMeasure(), p.isActive(), qty, p.getReorderLevel(), low,
                showCost ? avg : null, showCost ? MoneyPolicy.amount(qty.multiply(avg)) : null,
                p.isTrackBatches(), b == null ? null : b.getLastMovementAt());
    }

    static BatchDto toBatch(InventoryBatch b, LocalDate today, boolean showCost) {
        Long days = b.getExpiryDate() == null ? null : ChronoUnit.DAYS.between(today, b.getExpiryDate());
        return new BatchDto(b.getId(), b.getBatchNumber(), b.getExpiryDate(), b.getReceivedAt(),
                showCost ? b.getUnitCost() : null, b.getQuantityReceived(), b.getQuantityRemaining(),
                b.getStatus().name(), b.isExpired(today), days);
    }

    static MovementDto toMovement(StockMovement m, Product p) {
        return new MovementDto(m.getId(), m.getProductId(), p == null ? null : p.getName(),
                p == null ? null : p.getSku(), m.getMovementType(), m.getQuantityChange(), m.getQuantityBefore(),
                m.getQuantityAfter(), m.getUnitCost(), m.getBatchId(), m.getReferenceType(), m.getReferenceId(),
                m.getReason(), m.getPerformedBy(), m.getCreatedAt());
    }
}
