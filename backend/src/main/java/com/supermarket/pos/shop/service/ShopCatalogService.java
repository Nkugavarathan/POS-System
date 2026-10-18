package com.supermarket.pos.shop.service;

import com.supermarket.pos.categories.dto.CategoryDtos.CategoryNodeDto;
import com.supermarket.pos.categories.service.CategoryService;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.inventory.entity.InventoryBatch;
import com.supermarket.pos.inventory.service.InventoryService;
import com.supermarket.pos.products.entity.Product;
import com.supermarket.pos.products.entity.ProductPromotion;
import com.supermarket.pos.products.service.ProductService;
import com.supermarket.pos.products.service.StockLookup;
import com.supermarket.pos.products.service.StockLookup.StockLevel;
import com.supermarket.pos.sales.service.PricingEngine;
import com.supermarket.pos.sales.service.PricingEngine.LineInput;
import com.supermarket.pos.sales.service.PricingEngine.PricedCart;
import com.supermarket.pos.shop.dto.ShopDtos.CartLineDto;
import com.supermarket.pos.shop.dto.ShopDtos.CartQuoteDto;
import com.supermarket.pos.shop.dto.ShopDtos.CartQuoteRequest;
import com.supermarket.pos.shop.dto.ShopDtos.RescueDealDto;
import com.supermarket.pos.shop.dto.ShopDtos.ShopCategoryDto;
import com.supermarket.pos.shop.dto.ShopDtos.ShopProductDto;
import com.supermarket.pos.shop.dto.ShopDtos.ShopStoreDto;
import com.supermarket.pos.store.entity.Store;
import com.supermarket.pos.store.service.SettingsService;
import com.supermarket.pos.store.service.StoreService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Public online-shop catalog: active products only, prices with the store's promotions (including
 * rescue deals), and a coarse stock level per store (exact quantities and costs stay private).
 */
@Service
public class ShopCatalogService {

    private final ProductService products;
    private final CategoryService categories;
    private final StockLookup stock;
    private final InventoryService inventory;
    private final PricingEngine pricing;
    private final StoreService stores;
    private final SettingsService settings;
    private final Clock clock;

    public ShopCatalogService(ProductService products, CategoryService categories, StockLookup stock,
                              InventoryService inventory, PricingEngine pricing, StoreService stores,
                              SettingsService settings, Clock clock) {
        this.products = products;
        this.categories = categories;
        this.stock = stock;
        this.inventory = inventory;
        this.pricing = pricing;
        this.stores = stores;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ShopStoreDto> stores() {
        return stores.activeStores().stream()
                .filter(s -> settings.bool(s.getId(), "shop.enabled", true))
                .map(ShopCatalogService::toStore).toList();
    }

    @Transactional(readOnly = true)
    public List<ShopCategoryDto> categories() {
        return categories.activeTree().stream().map(ShopCatalogService::toCategory).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ShopProductDto> products(Long storeId, String q, Long categoryId, Integer page, Integer size,
                                                 List<String> sort) {
        Store store = requireShopStore(storeId);
        Page<Product> result = products.browseActive(q, categoryId, page, Math.min(size == null ? 24 : size, 48), sort);
        Map<Long, ShopProductDto> mapped = map(store, result.getContent());
        return PageResponse.of(result, p -> mapped.get(p.getId()));
    }

    @Transactional(readOnly = true)
    public ShopProductDto product(Long storeId, Long productId) {
        Store store = requireShopStore(storeId);
        Product product = products.loadForPricing(List.of(productId)).get(productId);
        if (product == null || !product.isActive()) {
            throw ApiException.notFound("Product", productId);
        }
        return map(store, List.of(product)).get(productId);
    }

    /** Products on rescue deals right now, soonest expiry first. */
    @Transactional(readOnly = true)
    public List<RescueDealDto> rescueDeals(Long storeId) {
        Store store = requireShopStore(storeId);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneId.of(store.getTimezone()));
        Map<Long, RescueDealDto> best = new LinkedHashMap<>();
        for (ProductPromotion promo : products.runningRescue(storeId, now)) {
            InventoryBatch batch = promo.getBatchId() == null ? null : inventory.batch(promo.getBatchId()).orElse(null);
            if (batch == null || batch.getQuantityRemaining().signum() == 0 || batch.getExpiryDate() == null) {
                continue;
            }
            Product p = promo.getProduct();
            BigDecimal offer = p.getSellingPrice().subtract(ProductService.perUnitDiscount(promo, p.getSellingPrice()));
            RescueDealDto dto = new RescueDealDto(p.getId(), p.getName(), p.getBrand(), p.getUnitOfMeasure().name(),
                    p.getSellingPrice(), offer, promo.getDiscountValue(), batch.getExpiryDate(),
                    ChronoUnit.DAYS.between(today, batch.getExpiryDate()), batch.getQuantityRemaining(), promo.getEndsAt(),
                    p.getImageUrl());
            best.merge(p.getId(), dto, (a, b) -> a.offerPrice().compareTo(b.offerPrice()) <= 0 ? a : b);
        }
        return new ArrayList<>(best.values());
    }

    /** Prices a basket for the shop (same engine as the till; no manual discounts online). */
    @Transactional(readOnly = true)
    public CartQuoteDto quote(CartQuoteRequest request) {
        Store store = requireShopStore(request.storeId());
        PricedCart cart = pricing.price(store.getId(), request.items().stream()
                .map(i -> new LineInput(i.productId(), i.quantity(), null, null)).toList(), null, store.isPricesIncludeTax());
        Map<Long, BigDecimal> requested = new LinkedHashMap<>();
        cart.lines().forEach(l -> requested.merge(l.product().getId(), l.quantity(), BigDecimal::add));
        Map<Long, Product> productMap = cart.lines().stream()
                .collect(Collectors.toMap(l -> l.product().getId(), PricingEngine.PricedLine::product, (a, b) -> a));
        var shortages = inventory.shortages(store.getId(), requested, productMap);
        List<CartLineDto> lines = cart.lines().stream().map(l -> new CartLineDto(l.product().getId(), l.product().getName(),
                l.product().getUnitOfMeasure().name(), l.quantity(), l.unitPrice(), l.promo(),
                l.promotion() == null ? null : l.promotion().getName(), l.lineTotal(),
                !shortages.containsKey(l.product().getId()))).toList();
        return new CartQuoteDto(store.getCurrency(), cart.pricesIncludeTax(), lines, cart.subtotal(), cart.discountTotal(),
                cart.taxTotal(), cart.grandTotal(), new ArrayList<>(shortages.keySet()), cart.discountTotal());
    }

    public Store requireShopStore(Long storeId) {
        Store store = stores.requireStore(storeId);
        if (!store.isActive() || !settings.bool(storeId, "shop.enabled", true)) {
            throw ApiException.rule("Online ordering is not available for this store.");
        }
        return store;
    }

    /** Maps products to shop cards: price, best offer (incl. rescue), coarse stock level. */
    public Map<Long, ShopProductDto> map(Store store, Collection<Product> list) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneId.of(store.getTimezone()));
        Map<Long, ProductPromotion> promos = products.bestPromotions(list, now, store.getId());
        Map<Long, StockLevel> levels = stock.levels(store.getId(), list.stream().map(Product::getId).toList());
        Map<Long, ShopProductDto> out = new LinkedHashMap<>();
        for (Product p : list) {
            ProductPromotion promo = promos.get(p.getId());
            BigDecimal offer = promo == null ? null : MoneyPolicy.amount(p.getSellingPrice()
                    .subtract(ProductService.perUnitDiscount(promo, p.getSellingPrice())));
            boolean rescue = promo != null && "RESCUE".equals(promo.getSource());
            Long daysLeft = null;
            if (rescue && promo.getBatchId() != null) {
                daysLeft = inventory.batch(promo.getBatchId()).map(InventoryBatch::getExpiryDate)
                        .map(d -> ChronoUnit.DAYS.between(today, d)).orElse(null);
            }
            String label = promo == null ? null : promo.getDiscountType() == ProductPromotion.DiscountType.PERCENT
                    ? promo.getDiscountValue().setScale(0, RoundingMode.HALF_UP).toPlainString() + "% off"
                    : "Save " + promo.getDiscountValue().toPlainString();
            out.put(p.getId(), new ShopProductDto(p.getId(), p.getName(), p.getBrand(), p.getDescription(),
                    p.getCategory().getId(), p.getCategory().getName(), p.getUnitOfMeasure().name(),
                    p.getUnitOfMeasure().isFractional(), p.getSellingPrice(), offer, label, rescue, daysLeft,
                    stockLevel(p, levels.get(p.getId())), p.getImageUrl()));
        }
        return out;
    }

    static String stockLevel(Product p, StockLevel level) {
        BigDecimal qty = level == null ? BigDecimal.ZERO : level.quantityOnHand();
        if (qty.signum() <= 0) {
            return "OUT";
        }
        BigDecimal low = p.getReorderLevel().signum() > 0 ? p.getReorderLevel() : new BigDecimal("3");
        return qty.compareTo(low) <= 0 ? "LOW" : "IN_STOCK";
    }

    static ShopStoreDto toStore(Store s) {
        return new ShopStoreDto(s.getId(), s.getCode(), s.getName(), s.getAddressLine1(), s.getCity(), s.getPhone());
    }

    private static ShopCategoryDto toCategory(CategoryNodeDto n) {
        return new ShopCategoryDto(n.id(), n.name(), n.productCount(),
                n.children().stream().filter(CategoryNodeDto::active).map(ShopCatalogService::toCategory).toList());
    }
}
