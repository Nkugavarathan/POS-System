package com.supermarket.pos.products.service;

import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.categories.entity.Category;
import com.supermarket.pos.categories.service.CategoryService;
import com.supermarket.pos.categories.service.CategoryUsage;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.common.security.CurrentUser;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.common.web.PageRequests;
import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.products.dto.ProductDtos.BarcodeRequest;
import com.supermarket.pos.products.dto.ProductDtos.PosProductDto;
import com.supermarket.pos.products.dto.ProductDtos.ProductDto;
import com.supermarket.pos.products.dto.ProductDtos.ProductRequest;
import com.supermarket.pos.products.dto.ProductDtos.ProductSummaryDto;
import com.supermarket.pos.products.dto.ProductDtos.PromotionDto;
import com.supermarket.pos.products.dto.ProductDtos.PromotionRequest;
import com.supermarket.pos.products.entity.Product;
import com.supermarket.pos.products.entity.ProductBarcode;
import com.supermarket.pos.products.entity.ProductPromotion;
import com.supermarket.pos.products.entity.TaxRate;
import com.supermarket.pos.products.mapper.ProductMapper;
import com.supermarket.pos.products.repository.ProductBarcodeRepository;
import com.supermarket.pos.products.repository.ProductPromotionRepository;
import com.supermarket.pos.products.repository.ProductRepository;
import com.supermarket.pos.products.repository.TaxRateRepository;
import com.supermarket.pos.products.service.BarcodeNormalizer.Normalized;
import com.supermarket.pos.products.service.StockLookup.StockLevel;
import com.supermarket.pos.store.service.StoreService;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catalog rules: unique SKU and normalized barcodes, price changes only with PRICE_MANAGE
 * (audited with old and new values), cost visible only with PRODUCT_VIEW_COST.
 */
@Service
public class ProductService implements CategoryUsage {

    private static final Map<String, String> SORTS = Map.of(
            "name", "name", "sku", "sku", "sellingPrice", "sellingPrice", "updatedAt", "updatedAt",
            "createdAt", "createdAt", "brand", "brand");
    private static final List<Long> NONE = List.of(-1L);

    private final ProductRepository products;
    private final ProductBarcodeRepository barcodes;
    private final ProductPromotionRepository promotions;
    private final TaxRateRepository taxRates;
    private final CategoryService categories;
    private final StockLookup stock;
    private final StoreService stores;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;
    private final Clock clock;

    public ProductService(ProductRepository products, ProductBarcodeRepository barcodes,
                          ProductPromotionRepository promotions, TaxRateRepository taxRates,
                          @Lazy CategoryService categories,
                          @Lazy StockLookup stock, StoreService stores,
                          CurrentUserProvider currentUser, AuditService audit, Clock clock) {
        this.products = products;
        this.barcodes = barcodes;
        this.promotions = promotions;
        this.taxRates = taxRates;
        this.categories = categories;
        this.stock = stock;
        this.stores = stores;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    public record SearchCriteria(String q, Long categoryId, Boolean active, String brand, boolean lowStock,
                                 Integer page, Integer size, List<String> sort) {
    }

    // ----- queries -------------------------------------------------------------------------

    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryDto> search(SearchCriteria criteria) {
        CurrentUser user = currentUser.require();
        Long storeId = user.requireActiveStore();
        Page<Product> page = runSearch(criteria, storeId);
        Map<Long, StockLevel> levels = stock.levels(storeId, page.getContent().stream().map(Product::getId).toList());
        boolean showCost = user.has("PRODUCT_VIEW_COST");
        return PageResponse.of(page, p -> ProductMapper.toSummary(p, showCost, storeId, levels.get(p.getId())));
    }

    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    @Transactional(readOnly = true)
    public ProductDto get(Long id) {
        CurrentUser user = currentUser.require();
        Long storeId = user.requireActiveStore();
        Product product = products.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("Product", id));
        StockLevel level = stock.levels(storeId, List.of(id)).get(id);
        boolean includeTax = stores.requireStore(storeId).isPricesIncludeTax();
        return ProductMapper.toDto(product, user.has("PRODUCT_VIEW_COST"), includeTax, storeId, level,
                promotions.findByProductIdOrderByStartsAtDesc(id), clock.instant());
    }

    /** Barcode scan / manual entry at the till. Identifies the product only; checkout re-prices. */
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    @Transactional(readOnly = true)
    public PosProductDto lookupByBarcode(String raw, String format) {
        Long storeId = currentUser.require().requireActiveStore();
        Normalized normalized = BarcodeNormalizer.normalize(raw, format);
        ProductBarcode match = barcodes.findForLookup(normalized.value())
                .orElseThrow(() -> new ApiException(ErrorCode.BARCODE_NOT_FOUND,
                        "No product has barcode " + normalized.value() + ".",
                        Map.of("barcode", normalized.value(), "type", normalized.type().name())));
        Product product = match.getProduct();
        if (!product.isActive()) {
            throw new ApiException(ErrorCode.PRODUCT_INACTIVE, product.getName() + " is not for sale (inactive).",
                    Map.of("productId", product.getId()));
        }
        Instant now = clock.instant();
        ProductPromotion promo = bestPromotions(List.of(product), now, storeId).get(product.getId());
        return ProductMapper.toPos(product, match.getBarcode(), promo,
                stock.levels(storeId, List.of(product.getId())).get(product.getId()), now);
    }

    /** Type-ahead for the cashier: active products by name, SKU prefix or exact barcode. */
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    @Transactional(readOnly = true)
    public List<PosProductDto> posSearch(String q, int limit) {
        Long storeId = currentUser.require().requireActiveStore();
        if (q == null || q.trim().length() < 2) {
            return List.of();
        }
        Page<Product> page = runSearch(new SearchCriteria(q, null, true, null, false, 0, Math.min(limit, 25),
                List.of("name,asc")), storeId);
        List<Product> found = page.getContent();
        Instant now = clock.instant();
        Map<Long, ProductPromotion> promos = bestPromotions(found, now, storeId);
        Map<Long, StockLevel> levels = stock.levels(storeId, found.stream().map(Product::getId).toList());
        return found.stream()
                .map(p -> ProductMapper.toPos(p, null, promos.get(p.getId()), levels.get(p.getId()), now))
                .toList();
    }

    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    @Transactional(readOnly = true)
    public List<String> brands() {
        return products.findBrands();
    }

    // ----- API for other modules (pricing, inventory, purchasing) ------------------------------

    /** Products with tax rate and category loaded, keyed by id. Missing ids are simply absent. */
    @Transactional(readOnly = true)
    public Map<Long, Product> loadForPricing(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return products.findByIdIn(new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    /** The promotion worth most to the customer per product (largest per-unit discount). */
    @Transactional(readOnly = true)
    public Map<Long, ProductPromotion> bestPromotions(Collection<Product> forProducts, Instant now, Long storeId) {
        if (forProducts.isEmpty()) {
            return Map.of();
        }
        Map<Long, Product> byId = forProducts.stream().collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        Map<Long, ProductPromotion> best = new HashMap<>();
        for (ProductPromotion promo : promotions.findRunning(byId.keySet(), now, storeId)) {
            Long pid = promo.getProduct().getId();
            BigDecimal value = perUnitDiscount(promo, byId.get(pid).getSellingPrice());
            ProductPromotion current = best.get(pid);
            if (current == null || value.compareTo(perUnitDiscount(current, byId.get(pid).getSellingPrice())) > 0) {
                best.put(pid, promo);
            }
        }
        return best;
    }

    /** Discount per unit for a promotion, capped at the unit price. */
    public static BigDecimal perUnitDiscount(ProductPromotion promo, BigDecimal unitPrice) {
        BigDecimal d = promo.getDiscountType() == ProductPromotion.DiscountType.PERCENT
                ? MoneyPolicy.percentOf(unitPrice, promo.getDiscountValue())
                : MoneyPolicy.amount(promo.getDiscountValue());
        return d.min(unitPrice);
    }

    /** Public browse (online shop): active products only, no permission and no cost data involved. */
    @Transactional(readOnly = true)
    public Page<Product> browseActive(String q, Long categoryId, Integer page, Integer size, List<String> sort) {
        return runSearch(new SearchCriteria(q, categoryId, true, null, false, page, size, sort), null);
    }

    /** Running rescue (near-expiry) promotions of a store, with their products. */
    @Transactional(readOnly = true)
    public List<ProductPromotion> runningRescue(Long storeId, Instant now) {
        return promotions.findRunningRescue(storeId, now);
    }

    /** True when the batch already has a rescue deal that is still running at {@code now}. */
    @Transactional(readOnly = true)
    public boolean hasActiveRescue(Long batchId, Instant now) {
        return promotions.existsByBatchIdAndSourceAndActiveTrueAndEndsAtAfter(batchId, "RESCUE", now);
    }

    /** Switches off rescue deals whose end time has passed; returns how many. */
    @Transactional
    public int closeLapsedRescue(Long storeId, Instant now) {
        List<ProductPromotion> lapsed = promotions.findLapsedRescue(storeId, now);
        lapsed.forEach(p -> p.setActive(false));
        return lapsed.size();
    }

    /** Creates a store-only promotion for one near-expiry batch (system generated, audited by the caller). */
    @Transactional
    public ProductPromotion createRescuePromotion(Long productId, Long storeId, Long batchId, BigDecimal percent,
                                                  Instant endsAt, String name) {
        ProductPromotion promo = new ProductPromotion();
        promo.setProduct(require(productId));
        promo.setName(name.length() > 100 ? name.substring(0, 100) : name);
        promo.setDiscountType(ProductPromotion.DiscountType.PERCENT);
        promo.setDiscountValue(MoneyPolicy.amount(percent));
        promo.setStartsAt(clock.instant());
        promo.setEndsAt(endsAt);
        promo.setStoreId(storeId);
        promo.setSource("RESCUE");
        promo.setBatchId(batchId);
        return promotions.saveAndFlush(promo);
    }

    /** Ends a rescue promotion early (batch sold out or written off). */
    @Transactional
    public void endPromotion(Long promotionId) {
        promotions.findById(promotionId).ifPresent(p -> p.setActive(false));
    }

    /** Active products in a category subtree (or all when {@code categoryId} is null), one query. */
    @Transactional(readOnly = true)
    public List<Product> activeProducts(Long categoryId) {
        return categoryId == null
                ? products.findActiveInCategories(true, NONE)
                : products.findActiveInCategories(false, categories.withDescendants(categoryId));
    }

    @Transactional(readOnly = true)
    public Product require(Long id) {
        return products.findById(id).orElseThrow(() -> ApiException.notFound("Product", id));
    }

    // ----- commands --------------------------------------------------------------------------

    @PreAuthorize("hasAuthority('PRODUCT_MANAGE')")
    @Transactional
    public ProductDto create(ProductRequest request) {
        CurrentUser user = currentUser.require();
        String sku = request.sku().trim().toUpperCase();
        if (products.existsBySkuIgnoreCase(sku)) {
            throw ApiException.duplicate("sku", "SKU " + sku + " already exists.");
        }
        List<Normalized> normalizedBarcodes = normalizeUnique(request.barcodes() == null ? List.of() : request.barcodes());
        Product product = new Product();
        product.setSku(sku);
        product.setCreatedBy(user.id());
        applyFields(product, request);
        product.setCostPrice(MoneyPolicy.cost(request.costPrice()));
        product.setSellingPrice(MoneyPolicy.amount(request.sellingPrice()));
        boolean first = true;
        for (Normalized n : normalizedBarcodes) {
            ProductBarcode b = new ProductBarcode();
            b.setBarcode(n.value());
            b.setBarcodeType(n.type());
            b.setPrimary(first);
            first = false;
            product.addBarcode(b);
        }
        Product saved = products.saveAndFlush(product);
        audit.record(AuditService.Entry.success("PRODUCT_CREATED", "Product", saved.getId(),
                "Product " + sku + " created").withDetails(Map.of(
                "sellingPrice", saved.getSellingPrice().toPlainString(), "barcodes",
                normalizedBarcodes.stream().map(Normalized::value).toList())));
        return get(saved.getId());
    }

    @PreAuthorize("hasAuthority('PRODUCT_MANAGE')")
    @Transactional
    public ProductDto update(Long id, ProductRequest request) {
        CurrentUser user = currentUser.require();
        Product product = products.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("Product", id));
        checkVersion(product.getVersion(), request.version());
        String sku = request.sku().trim().toUpperCase();
        if (!product.getSku().equalsIgnoreCase(sku) && products.existsBySkuIgnoreCase(sku)) {
            throw ApiException.duplicate("sku", "SKU " + sku + " already exists.");
        }
        BigDecimal newPrice = MoneyPolicy.amount(request.sellingPrice());
        BigDecimal newCost = MoneyPolicy.cost(request.costPrice());
        boolean priceChanged = product.getSellingPrice().compareTo(newPrice) != 0;
        boolean costChanged = product.getCostPrice().compareTo(newCost) != 0;
        if ((priceChanged || costChanged) && !user.has("PRICE_MANAGE")) {
            throw ApiException.forbidden("Changing prices requires the PRICE_MANAGE permission.");
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        if (priceChanged) {
            changes.put("sellingPrice", Map.of("old", product.getSellingPrice().toPlainString(), "new", newPrice.toPlainString()));
        }
        if (costChanged) {
            changes.put("costPrice", Map.of("old", product.getCostPrice().toPlainString(), "new", newCost.toPlainString()));
        }
        product.setSku(sku);
        applyFields(product, request);
        product.setSellingPrice(newPrice);
        product.setCostPrice(newCost);
        products.saveAndFlush(product);
        audit.record(AuditService.Entry.success(priceChanged ? "PRICE_CHANGED" : "PRODUCT_UPDATED", "Product", id,
                "Product " + sku + " updated").withDetails(changes));
        return get(id);
    }

    @PreAuthorize("hasAuthority('PRODUCT_MANAGE')")
    @Transactional
    public ProductDto setActive(Long id, boolean active, Long version) {
        Product product = require(id);
        checkVersion(product.getVersion(), version);
        product.setActive(active);
        products.saveAndFlush(product);
        audit.record(AuditService.Entry.success(active ? "PRODUCT_ACTIVATED" : "PRODUCT_DEACTIVATED", "Product", id,
                product.getSku()));
        return get(id);
    }

    @PreAuthorize("hasAuthority('PRODUCT_MANAGE')")
    @Transactional
    public ProductDto addBarcode(Long productId, BarcodeRequest request) {
        Product product = products.findWithDetailsById(productId)
                .orElseThrow(() -> ApiException.notFound("Product", productId));
        Normalized n = BarcodeNormalizer.normalize(request.barcode(), request.format());
        barcodes.findByBarcodeIn(List.of(n.value())).stream().findFirst().ifPresent(existing -> {
            throw ApiException.duplicate("barcode", "Barcode " + n.value() + " already belongs to "
                    + existing.getProduct().getSku() + ".");
        });
        boolean primary = request.primary() || product.getBarcodes().isEmpty();
        if (primary) {
            product.getBarcodes().forEach(b -> b.setPrimary(false));
        }
        ProductBarcode barcode = new ProductBarcode();
        barcode.setBarcode(n.value());
        barcode.setBarcodeType(n.type());
        barcode.setPrimary(primary);
        product.addBarcode(barcode);
        products.saveAndFlush(product);
        audit.record(AuditService.Entry.success("BARCODE_ADDED", "Product", productId, n.value()));
        return get(productId);
    }

    @PreAuthorize("hasAuthority('PRODUCT_MANAGE')")
    @Transactional
    public ProductDto removeBarcode(Long productId, Long barcodeId) {
        Product product = products.findWithDetailsById(productId)
                .orElseThrow(() -> ApiException.notFound("Product", productId));
        ProductBarcode target = product.getBarcodes().stream().filter(b -> b.getId().equals(barcodeId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Barcode", barcodeId));
        if (product.getBarcodes().size() == 1) {
            throw ApiException.rule("A product must keep at least one barcode while it has any. "
                    + "Add the replacement first.");
        }
        product.getBarcodes().remove(target);
        if (target.isPrimary()) {
            product.getBarcodes().stream().min(Comparator.comparing(ProductBarcode::getId))
                    .ifPresent(b -> b.setPrimary(true));
        }
        products.saveAndFlush(product);
        audit.record(AuditService.Entry.success("BARCODE_REMOVED", "Product", productId, target.getBarcode()));
        return get(productId);
    }

    @PreAuthorize("hasAuthority('PRICE_MANAGE')")
    @Transactional
    public PromotionDto createPromotion(Long productId, PromotionRequest request) {
        Product product = require(productId);
        validatePromotion(product, request);
        ProductPromotion promo = new ProductPromotion();
        promo.setProduct(product);
        promo.setCreatedBy(currentUser.require().id());
        applyPromotion(promo, request);
        ProductPromotion saved = promotions.saveAndFlush(promo);
        audit.record(AuditService.Entry.success("PROMOTION_CREATED", "Product", productId, request.name())
                .withDetails(Map.of("type", request.type().name(), "value", request.value().toPlainString(),
                        "startsAt", request.startsAt().toString(), "endsAt", request.endsAt().toString())));
        return ProductMapper.toPromotion(saved, clock.instant());
    }

    @PreAuthorize("hasAuthority('PRICE_MANAGE')")
    @Transactional
    public PromotionDto updatePromotion(Long productId, Long promotionId, PromotionRequest request) {
        ProductPromotion promo = promotions.findById(promotionId)
                .filter(p -> p.getProduct().getId().equals(productId))
                .orElseThrow(() -> ApiException.notFound("Promotion", promotionId));
        checkVersion(promo.getVersion(), request.version());
        validatePromotion(promo.getProduct(), request);
        applyPromotion(promo, request);
        ProductPromotion saved = promotions.saveAndFlush(promo);
        audit.record(AuditService.Entry.success("PROMOTION_UPDATED", "Product", productId, request.name()));
        return ProductMapper.toPromotion(saved, clock.instant());
    }

    // ----- CategoryUsage ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> activeProductCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : products.countActiveByCategory()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public long activeProductCount(Long categoryId) {
        return products.countByCategoryIdAndActiveTrue(categoryId);
    }

    // ----- helpers -----------------------------------------------------------------------------

    private Page<Product> runSearch(SearchCriteria c, Long storeId) {
        var pageable = PageRequests.of(c.page(), c.size(), c.sort(), SORTS, Sort.by("name"));
        String q = c.q() == null || c.q().isBlank() ? null : c.q().trim();
        String barcode = q == null ? null : tryNormalize(q);
        Collection<Long> categoryIds = c.categoryId() == null ? NONE : categories.withDescendants(c.categoryId());
        Collection<Long> ids = NONE;
        if (c.lowStock()) {
            Set<Long> low = stock.lowStockProductIds(storeId);
            if (low.isEmpty()) {
                return Page.empty(pageable);
            }
            ids = low;
        }
        String brand = c.brand() == null || c.brand().isBlank() ? null : c.brand().trim();
        return products.search(q, barcode, c.categoryId() == null, categoryIds, c.active(), brand,
                !c.lowStock(), ids, pageable);
    }

    private static String tryNormalize(String q) {
        try {
            return BarcodeNormalizer.normalize(q).value();
        } catch (ApiException notABarcode) {
            return "\u0000"; // matches nothing
        }
    }

    private void applyFields(Product product, ProductRequest request) {
        Category category = categories.requireActive(request.categoryId());
        TaxRate taxRate = taxRates.findById(request.taxRateId())
                .orElseThrow(() -> ApiException.notFound("Tax rate", request.taxRateId()));
        if (!taxRate.isActive() && !Objects.equals(product.getTaxRate(), taxRate)) {
            throw ApiException.rule("Tax rate " + taxRate.getCode() + " is inactive.");
        }
        if (!request.unitOfMeasure().isFractional() && request.reorderLevel().stripTrailingZeros().scale() > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Reorder level must be a whole number for " + request.unitOfMeasure() + " products.");
        }
        if (request.trackExpiry() && !request.trackBatches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Expiry tracking requires batch tracking.");
        }
        product.setName(request.name().trim());
        product.setDescription(blankToNull(request.description()));
        product.setCategory(category);
        product.setBrand(blankToNull(request.brand()));
        product.setUnitOfMeasure(request.unitOfMeasure());
        product.setTaxRate(taxRate);
        product.setReorderLevel(MoneyPolicy.quantity(request.reorderLevel()));
        product.setTrackBatches(request.trackBatches());
        product.setTrackExpiry(request.trackExpiry());
        product.setAllowPriceOverride(request.allowPriceOverride());
        product.setImageUrl(blankToNull(request.imageUrl()));
    }

    private List<Normalized> normalizeUnique(List<String> raw) {
        Map<String, Normalized> unique = new LinkedHashMap<>();
        for (String code : raw) {
            Normalized n = BarcodeNormalizer.normalize(code);
            unique.putIfAbsent(n.value(), n);
        }
        if (!unique.isEmpty()) {
            List<ProductBarcode> taken = barcodes.findByBarcodeIn(unique.keySet());
            if (!taken.isEmpty()) {
                ProductBarcode t = taken.get(0);
                throw ApiException.duplicate("barcodes", "Barcode " + t.getBarcode() + " already belongs to "
                        + t.getProduct().getSku() + ".");
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static void validatePromotion(Product product, PromotionRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The promotion must end after it starts.");
        }
        if (request.type() == ProductPromotion.DiscountType.PERCENT && request.value().compareTo(MoneyPolicy.HUNDRED) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "A percentage promotion cannot exceed 100%.");
        }
        if (request.type() == ProductPromotion.DiscountType.AMOUNT
                && request.value().compareTo(product.getSellingPrice()) >= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "An amount promotion must be less than the selling price (" + product.getSellingPrice() + ").");
        }
    }

    private static void applyPromotion(ProductPromotion promo, PromotionRequest request) {
        promo.setName(request.name().trim());
        promo.setDiscountType(request.type());
        promo.setDiscountValue(MoneyPolicy.amount(request.value()));
        promo.setStartsAt(request.startsAt());
        promo.setEndsAt(request.endsAt());
        promo.setActive(request.active());
    }

    private static void checkVersion(long current, Long expected) {
        if (expected == null || current != expected) {
            throw new OptimisticLockingFailureException("Version mismatch");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
