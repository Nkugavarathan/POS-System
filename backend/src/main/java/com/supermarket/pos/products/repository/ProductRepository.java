package com.supermarket.pos.products.repository;

import com.supermarket.pos.products.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Filtered product page. Category and tax rate are fetched with the page (to-one joins are
     * safe with pagination); barcodes load in one batched query (default_batch_fetch_size).
     * {@code q} matches name, SKU, brand, or an exact normalized barcode.
     */
    @EntityGraph(attributePaths = {"category", "taxRate"})
    @Query(value = """
            select p from Product p
            where (:q is null
                   or lower(p.name) like lower(concat('%', :q, '%'))
                   or lower(p.sku) like lower(concat(:q, '%'))
                   or lower(p.brand) like lower(concat('%', :q, '%'))
                   or exists (select 1 from ProductBarcode b where b.product = p and b.barcode = :barcode))
              and (:categoriesEmpty = true or p.category.id in :categoryIds)
              and (:active is null or p.active = :active)
              and (:brand is null or lower(p.brand) = lower(:brand))
              and (:idsEmpty = true or p.id in :ids)
            """,
            countQuery = """
            select count(p) from Product p
            where (:q is null
                   or lower(p.name) like lower(concat('%', :q, '%'))
                   or lower(p.sku) like lower(concat(:q, '%'))
                   or lower(p.brand) like lower(concat('%', :q, '%'))
                   or exists (select 1 from ProductBarcode b where b.product = p and b.barcode = :barcode))
              and (:categoriesEmpty = true or p.category.id in :categoryIds)
              and (:active is null or p.active = :active)
              and (:brand is null or lower(p.brand) = lower(:brand))
              and (:idsEmpty = true or p.id in :ids)
            """)
    Page<Product> search(@Param("q") String q,
                         @Param("barcode") String barcode,
                         @Param("categoriesEmpty") boolean categoriesEmpty,
                         @Param("categoryIds") Collection<Long> categoryIds,
                         @Param("active") Boolean active,
                         @Param("brand") String brand,
                         @Param("idsEmpty") boolean idsEmpty,
                         @Param("ids") Collection<Long> ids,
                         Pageable pageable);

    @EntityGraph(attributePaths = {"category", "taxRate", "barcodes"})
    Optional<Product> findWithDetailsById(Long id);

    /** Products with tax rate for pricing; barcodes are loaded lazily in batches if needed. */
    @EntityGraph(attributePaths = {"taxRate", "category"})
    List<Product> findByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = {"category"})
    @Query("select p from Product p where p.active = true and (:all = true or p.category.id in :categoryIds) order by p.name")
    List<Product> findActiveInCategories(@Param("all") boolean all, @Param("categoryIds") Collection<Long> categoryIds);

    boolean existsBySkuIgnoreCase(String sku);

    @Query("select p.category.id, count(p) from Product p where p.active = true group by p.category.id")
    List<Object[]> countActiveByCategory();

    long countByCategoryIdAndActiveTrue(Long categoryId);

    @Query("select distinct p.brand from Product p where p.brand is not null order by p.brand")
    List<String> findBrands();
}
