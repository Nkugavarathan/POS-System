package com.supermarket.pos.products.repository;

import com.supermarket.pos.products.entity.ProductPromotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ProductPromotionRepository extends JpaRepository<ProductPromotion, Long> {

    @Query("""
            select pr from ProductPromotion pr
            where pr.product.id in :productIds and pr.active = true
              and pr.startsAt <= :now and pr.endsAt > :now
              and (pr.storeId is null or pr.storeId = :storeId)
            """)
    List<ProductPromotion> findRunning(@Param("productIds") Collection<Long> productIds, @Param("now") Instant now,
                                       @Param("storeId") Long storeId);

    @Query("""
            select pr from ProductPromotion pr join fetch pr.product p
            where pr.source = 'RESCUE' and pr.active = true and pr.storeId = :storeId
              and pr.startsAt <= :now and pr.endsAt > :now and p.active = true
            order by pr.endsAt
            """)
    List<ProductPromotion> findRunningRescue(@Param("storeId") Long storeId, @Param("now") Instant now);

    /** A running (active and not yet ended) rescue deal for this batch. */
    boolean existsByBatchIdAndSourceAndActiveTrueAndEndsAtAfter(Long batchId, String source, Instant now);

    /** Rescue deals whose end time has passed but are still flagged active. */
    @Query("""
            select pr from ProductPromotion pr
            where pr.source = 'RESCUE' and pr.active = true and pr.storeId = :storeId and pr.endsAt <= :now
            """)
    List<ProductPromotion> findLapsedRescue(@Param("storeId") Long storeId, @Param("now") Instant now);

    List<ProductPromotion> findByProductIdOrderByStartsAtDesc(Long productId);
}
