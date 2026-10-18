package com.supermarket.pos.inventory.repository;

import com.supermarket.pos.inventory.entity.InventoryBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {

    /** FEFO order: earliest expiry first, undated batches last, then oldest receipt. Locked. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from InventoryBatch b
            where b.storeId = :storeId and b.productId = :productId
              and b.status = com.supermarket.pos.inventory.entity.InventoryBatch.Status.ACTIVE
              and b.quantityRemaining > 0
            order by case when b.expiryDate is null then 1 else 0 end, b.expiryDate, b.receivedAt, b.id
            """)
    List<InventoryBatch> lockAvailableFefo(@Param("storeId") Long storeId, @Param("productId") Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBatch b where b.id = :id")
    Optional<InventoryBatch> lockById(@Param("id") Long id);

    Optional<InventoryBatch> findByStoreIdAndProductIdAndBatchNumber(Long storeId, Long productId, String batchNumber);

    List<InventoryBatch> findByStoreIdAndProductIdOrderByExpiryDateAscReceivedAtAsc(Long storeId, Long productId);

    @Query("""
            select b from InventoryBatch b
            where b.storeId = :storeId
              and b.status = com.supermarket.pos.inventory.entity.InventoryBatch.Status.ACTIVE
              and b.quantityRemaining > 0 and b.expiryDate is not null and b.expiryDate <= :until
            order by b.expiryDate, b.id
            """)
    List<InventoryBatch> findExpiring(@Param("storeId") Long storeId, @Param("until") LocalDate until);
}
