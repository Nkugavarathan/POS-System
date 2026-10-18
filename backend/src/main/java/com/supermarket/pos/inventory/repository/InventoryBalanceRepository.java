package com.supermarket.pos.inventory.repository;

import com.supermarket.pos.inventory.entity.InventoryBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long> {

    /** Creates a zero balance row if missing. Idempotent and safe under concurrency. */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO inventory_balances (store_id, product_id, quantity_on_hand, average_cost, version)
            VALUES (:storeId, :productId, 0, 0, 0)
            """, nativeQuery = true)
    int ensureExists(@Param("storeId") Long storeId, @Param("productId") Long productId);

    /**
     * Row locks in ascending product id order. Every writer locks in the same order, so two
     * concurrent sales of overlapping baskets cannot deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from InventoryBalance b
            where b.storeId = :storeId and b.productId in :productIds
            order by b.productId
            """)
    List<InventoryBalance> lockForUpdate(@Param("storeId") Long storeId,
                                         @Param("productIds") Collection<Long> productIds);

    List<InventoryBalance> findByStoreIdAndProductIdIn(Long storeId, Collection<Long> productIds);

    /** Ids only: does not put (soon stale) entities into the persistence context before locking. */
    @Query("select b.productId from InventoryBalance b where b.storeId = :storeId and b.productId in :productIds")
    List<Long> findExistingProductIds(@Param("storeId") Long storeId, @Param("productIds") Collection<Long> productIds);

    /** Active products at or below reorder level, including products never stocked in this store. */
    @Query("""
            select p.id from Product p
            left join InventoryBalance b on b.productId = p.id and b.storeId = :storeId
            where p.active = true and p.reorderLevel > 0
              and coalesce(b.quantityOnHand, 0) <= p.reorderLevel
            """)
    List<Long> findLowStockProductIds(@Param("storeId") Long storeId);

    /** Inventory list rows: product, balance (may be null). */
    @Query(value = """
            select p, b from Product p
            join fetch p.category
            left join InventoryBalance b on b.productId = p.id and b.storeId = :storeId
            where (:q is null or lower(p.name) like lower(concat('%', :q, '%')) or lower(p.sku) like lower(concat(:q, '%')))
              and (:categoriesEmpty = true or p.category.id in :categoryIds)
              and (:includeInactive = true or p.active = true)
              and (:lowStock = false or (p.reorderLevel > 0 and coalesce(b.quantityOnHand, 0) <= p.reorderLevel))
              and (:inStockOnly = false or coalesce(b.quantityOnHand, 0) > 0)
            """,
            countQuery = """
            select count(p) from Product p
            left join InventoryBalance b on b.productId = p.id and b.storeId = :storeId
            where (:q is null or lower(p.name) like lower(concat('%', :q, '%')) or lower(p.sku) like lower(concat(:q, '%')))
              and (:categoriesEmpty = true or p.category.id in :categoryIds)
              and (:includeInactive = true or p.active = true)
              and (:lowStock = false or (p.reorderLevel > 0 and coalesce(b.quantityOnHand, 0) <= p.reorderLevel))
              and (:inStockOnly = false or coalesce(b.quantityOnHand, 0) > 0)
            """)
    Page<Object[]> list(@Param("storeId") Long storeId,
                        @Param("q") String q,
                        @Param("categoriesEmpty") boolean categoriesEmpty,
                        @Param("categoryIds") Collection<Long> categoryIds,
                        @Param("includeInactive") boolean includeInactive,
                        @Param("lowStock") boolean lowStock,
                        @Param("inStockOnly") boolean inStockOnly,
                        Pageable pageable);
}
