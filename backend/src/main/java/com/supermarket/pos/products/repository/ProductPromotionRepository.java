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
            """)
    List<ProductPromotion> findRunning(@Param("productIds") Collection<Long> productIds, @Param("now") Instant now);

    List<ProductPromotion> findByProductIdOrderByStartsAtDesc(Long productId);
}
