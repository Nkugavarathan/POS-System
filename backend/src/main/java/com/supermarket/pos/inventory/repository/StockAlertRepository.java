package com.supermarket.pos.inventory.repository;

import com.supermarket.pos.inventory.entity.StockAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {

    Page<StockAlert> findByStoreIdAndAcknowledgedAtIsNull(Long storeId, Pageable pageable);

    @Query("""
            select a.productId from StockAlert a
            where a.storeId = :storeId and a.alertType = :type and a.acknowledgedAt is null
              and a.productId in :productIds
            """)
    List<Long> findOpenProductIds(@Param("storeId") Long storeId, @Param("type") StockAlert.Type type,
                                  @Param("productIds") Collection<Long> productIds);

    @Query("""
            select a.batchId from StockAlert a
            where a.storeId = :storeId and a.alertType = :type and a.batchId in :batchIds
            """)
    List<Long> findBatchIdsWithAlert(@Param("storeId") Long storeId, @Param("type") StockAlert.Type type,
                                     @Param("batchIds") Collection<Long> batchIds);

    long countByStoreIdAndAcknowledgedAtIsNull(Long storeId);
}
