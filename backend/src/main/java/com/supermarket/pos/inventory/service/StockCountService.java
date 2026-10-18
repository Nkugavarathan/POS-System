package com.supermarket.pos.inventory.service;

import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.common.security.CurrentUser;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.common.sequence.SequenceService;
import com.supermarket.pos.common.web.PageRequests;
import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.inventory.dto.InventoryDtos.CountDto;
import com.supermarket.pos.inventory.dto.InventoryDtos.CountEntry;
import com.supermarket.pos.inventory.dto.InventoryDtos.CountItemDto;
import com.supermarket.pos.inventory.dto.InventoryDtos.CreateCountRequest;
import com.supermarket.pos.inventory.entity.InventoryBalance;
import com.supermarket.pos.inventory.entity.MovementType;
import com.supermarket.pos.inventory.entity.StockCount;
import com.supermarket.pos.inventory.entity.StockCountItem;
import com.supermarket.pos.inventory.repository.InventoryBalanceRepository;
import com.supermarket.pos.inventory.repository.StockCountRepository;
import com.supermarket.pos.inventory.service.InventoryService.Deduction;
import com.supermarket.pos.inventory.service.InventoryService.Receipt;
import com.supermarket.pos.inventory.service.InventoryService.Reference;
import com.supermarket.pos.products.entity.Product;
import com.supermarket.pos.products.service.ProductService;
import com.supermarket.pos.products.service.QuantityRules;
import com.supermarket.pos.store.service.StoreService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Physical counts. Variances are applied against the live quantity at posting time, so sales
 * that happen while shelves are being counted are not lost; the snapshot taken at creation is
 * kept for reporting how much stock moved during the count.
 */
@Service
public class StockCountService {

    public static final String REFERENCE_TYPE = "STOCK_COUNT";
    private static final int MAX_ITEMS = 5000;

    private final StockCountRepository counts;
    private final InventoryBalanceRepository balances;
    private final InventoryService inventory;
    private final ProductService products;
    private final EntityManager entityManager;
    private final StoreService stores;
    private final SequenceService sequences;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;
    private final Clock clock;

    public StockCountService(StockCountRepository counts, InventoryBalanceRepository balances,
                             InventoryService inventory, ProductService products, EntityManager entityManager,
                             StoreService stores, SequenceService sequences, CurrentUserProvider currentUser,
                             AuditService audit, Clock clock) {
        this.counts = counts;
        this.balances = balances;
        this.inventory = inventory;
        this.products = products;
        this.entityManager = entityManager;
        this.stores = stores;
        this.sequences = sequences;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('INVENTORY_COUNT')")
    @Transactional
    public CountDto create(CreateCountRequest request) {
        CurrentUser user = currentUser.require();
        Long storeId = user.requireActiveStore();
        List<Product> scope = products.activeProducts(request.categoryId());
        if (scope.size() > MAX_ITEMS) {
            throw ApiException.rule("Too many products for one count; count one category at a time.");
        }
        if (scope.isEmpty()) {
            throw ApiException.rule("There are no active products to count in this scope.");
        }
        Map<Long, BigDecimal> onHand = balances.findByStoreIdAndProductIdIn(storeId,
                        scope.stream().map(Product::getId).toList()).stream()
                .collect(Collectors.toMap(InventoryBalance::getProductId, InventoryBalance::getQuantityOnHand));

        StockCount count = new StockCount();
        count.setStoreId(storeId);
        count.setCategoryId(request.categoryId());
        count.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        count.setCreatedBy(user.id());
        count.setCreatedAt(clock.instant());
        count.setCountNumber(sequences.nextNumber("STOCK_COUNT", "CNT", stores.requireStore(storeId).getCode(), 5));
        for (Product p : scope) {
            StockCountItem item = new StockCountItem();
            item.setStockCount(count);
            item.setProductId(p.getId());
            item.setSystemQuantity(onHand.getOrDefault(p.getId(), BigDecimal.ZERO.setScale(3)));
            count.getItems().add(item);
        }
        StockCount saved = counts.saveAndFlush(count);
        audit.record(AuditService.Entry.success("STOCK_COUNT_CREATED", "StockCount", saved.getId(),
                saved.getCountNumber() + " with " + scope.size() + " products").withStore(storeId));
        return toDto(saved, true);
    }

    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    @Transactional(readOnly = true)
    public CountDto get(Long id) {
        return toDto(load(id), true);
    }

    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    @Transactional(readOnly = true)
    public PageResponse<CountDto> list(Integer page, Integer size) {
        Long storeId = currentUser.require().requireActiveStore();
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.of(counts.findByStoreId(storeId, pageable), c -> toDto(c, false));
    }

    @PreAuthorize("hasAuthority('INVENTORY_COUNT')")
    @Transactional
    public CountDto record(Long id, List<CountEntry> entries, Long version) {
        StockCount count = load(id);
        requireStatus(count, StockCount.Status.OPEN);
        if (version == null || count.getVersion() != version) {
            throw new OptimisticLockingFailureException("Stock count changed");
        }
        Map<Long, StockCountItem> byProduct = count.getItems().stream()
                .collect(Collectors.toMap(StockCountItem::getProductId, Function.identity()));
        Map<Long, Product> productMap = products.loadForPricing(byProduct.keySet());
        for (CountEntry entry : entries) {
            StockCountItem item = byProduct.get(entry.productId());
            if (item == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Product " + entry.productId() + " is not part of this count.");
            }
            if (entry.countedQuantity() == null) {
                item.setCountedQuantity(null);
                continue;
            }
            if (entry.countedQuantity().signum() == 0) {
                item.setCountedQuantity(BigDecimal.ZERO.setScale(3));
            } else {
                item.setCountedQuantity(QuantityRules.validate(productMap.get(entry.productId()), entry.countedQuantity()));
            }
        }
        // Bump the header version so concurrent editors get a conflict instead of silently overwriting.
        entityManager.lock(count, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        entityManager.flush();
        return toDto(count, true);
    }

    @PreAuthorize("hasAuthority('INVENTORY_COUNT')")
    @Transactional
    public CountDto submit(Long id) {
        StockCount count = lock(id);
        requireStatus(count, StockCount.Status.OPEN);
        if (count.getItems().stream().noneMatch(i -> i.getCountedQuantity() != null)) {
            throw ApiException.rule("Record at least one counted quantity before submitting.");
        }
        count.setStatus(StockCount.Status.SUBMITTED);
        count.setSubmittedBy(currentUser.require().id());
        count.setSubmittedAt(clock.instant());
        audit.record(AuditService.Entry.success("STOCK_COUNT_SUBMITTED", "StockCount", id, count.getCountNumber())
                .withStore(count.getStoreId()));
        return toDto(count, true);
    }

    @PreAuthorize("hasAuthority('INVENTORY_COUNT_POST')")
    @Transactional
    public CountDto reopen(Long id) {
        StockCount count = lock(id);
        requireStatus(count, StockCount.Status.SUBMITTED);
        count.setStatus(StockCount.Status.OPEN);
        audit.record(AuditService.Entry.success("STOCK_COUNT_REOPENED", "StockCount", id, count.getCountNumber())
                .withStore(count.getStoreId()));
        return toDto(count, true);
    }

    @PreAuthorize("hasAuthority('INVENTORY_COUNT')")
    @Transactional
    public CountDto cancel(Long id) {
        StockCount count = lock(id);
        if (count.getStatus() == StockCount.Status.POSTED || count.getStatus() == StockCount.Status.CANCELLED) {
            throw ApiException.invalidState("A " + count.getStatus().name().toLowerCase() + " count cannot be cancelled.");
        }
        count.setStatus(StockCount.Status.CANCELLED);
        audit.record(AuditService.Entry.success("STOCK_COUNT_CANCELLED", "StockCount", id, count.getCountNumber())
                .withStore(count.getStoreId()));
        return toDto(count, true);
    }

    /** Applies variances (counted − live quantity) as COUNT_CORRECTION movements. */
    @PreAuthorize("hasAuthority('INVENTORY_COUNT_POST')")
    @Transactional
    public CountDto post(Long id) {
        CurrentUser user = currentUser.require();
        StockCount count = lock(id);
        requireStatus(count, StockCount.Status.SUBMITTED);
        List<StockCountItem> counted = count.getItems().stream().filter(i -> i.getCountedQuantity() != null).toList();
        Map<Long, BigDecimal> live = balances.findByStoreIdAndProductIdIn(count.getStoreId(),
                        counted.stream().map(StockCountItem::getProductId).toList()).stream()
                .collect(Collectors.toMap(InventoryBalance::getProductId, InventoryBalance::getQuantityOnHand));

        Reference ref = new Reference(REFERENCE_TYPE, count.getId(), user.id(), "Stock count " + count.getCountNumber());
        List<Deduction> outs = new ArrayList<>();
        List<Receipt> ins = new ArrayList<>();
        for (StockCountItem item : counted) {
            BigDecimal current = live.getOrDefault(item.getProductId(), BigDecimal.ZERO);
            BigDecimal variance = MoneyPolicy.quantity(item.getCountedQuantity().subtract(current));
            item.setQuantityAtPost(current);
            item.setVariance(variance);
            if (variance.signum() < 0) {
                outs.add(new Deduction(item.getProductId(), variance.negate(), item.getId()));
            } else if (variance.signum() > 0) {
                ins.add(new Receipt(item.getProductId(), variance, null, null, null, item.getId(), null, null));
            }
        }
        // The ledger re-checks under row locks; a sale between the read above and here can only
        // make a deduction fail safely (insufficient stock), never drive stock negative.
        inventory.deduct(count.getStoreId(), MovementType.COUNT_CORRECTION, ref, outs);
        inventory.receive(count.getStoreId(), MovementType.COUNT_CORRECTION, ref, ins);
        count.setStatus(StockCount.Status.POSTED);
        count.setPostedBy(user.id());
        count.setPostedAt(clock.instant());
        audit.record(AuditService.Entry.success("STOCK_COUNT_POSTED", "StockCount", id, count.getCountNumber())
                .withStore(count.getStoreId())
                .withDetails(Map.of("corrections", outs.size() + ins.size(), "countedItems", counted.size())));
        return toDto(count, true);
    }

    private StockCount load(Long id) {
        StockCount count = counts.findWithItemsById(id).orElseThrow(() -> ApiException.notFound("Stock count", id));
        currentUser.require().assertStoreAccess(count.getStoreId(), "Stock count", id);
        return count;
    }

    private StockCount lock(Long id) {
        StockCount count = counts.lockById(id).orElseThrow(() -> ApiException.notFound("Stock count", id));
        currentUser.require().assertStoreAccess(count.getStoreId(), "Stock count", id);
        count.getItems().size(); // initialize within the transaction
        return count;
    }

    private static void requireStatus(StockCount count, StockCount.Status expected) {
        if (count.getStatus() != expected) {
            throw ApiException.invalidState("The count is " + count.getStatus().name().toLowerCase()
                    + "; expected " + expected.name().toLowerCase() + ".");
        }
    }

    private CountDto toDto(StockCount c, boolean withItems) {
        List<CountItemDto> items = List.of();
        int countedCount = 0;
        if (withItems) {
            Map<Long, Product> productMap = products.loadForPricing(
                    c.getItems().stream().map(StockCountItem::getProductId).toList());
            items = c.getItems().stream().map(i -> {
                Product p = productMap.get(i.getProductId());
                return new CountItemDto(i.getId(), i.getProductId(), p == null ? null : p.getSku(),
                        p == null ? null : p.getName(), p == null ? null : p.getUnitOfMeasure(), i.getSystemQuantity(),
                        i.getCountedQuantity(), i.getQuantityAtPost(), i.getVariance());
            }).toList();
            countedCount = (int) c.getItems().stream().filter(i -> i.getCountedQuantity() != null).count();
        }
        return new CountDto(c.getId(), c.getCountNumber(), c.getStatus(), c.getCategoryId(), c.getNotes(),
                c.getCreatedBy(), c.getCreatedAt(), c.getSubmittedAt(), c.getPostedAt(),
                withItems ? c.getItems().size() : 0, countedCount, c.getVersion(), items);
    }
}
