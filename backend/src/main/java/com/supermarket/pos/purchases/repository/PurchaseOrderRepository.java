package com.supermarket.pos.purchases.repository;

import com.supermarket.pos.purchases.entity.PurchaseOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<PurchaseOrder> findWithItemsById(Long id);

    /** Serializes concurrent receipts against the same PO. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseOrder p where p.id = :id")
    Optional<PurchaseOrder> lockById(@Param("id") Long id);

    @Query(value = """
            select p from PurchaseOrder p
            where p.storeId = :storeId
              and (:supplierId is null or p.supplierId = :supplierId)
              and (:status is null or p.status = :status)
              and (:q is null or p.poNumber like concat('%', :q, '%'))
            """,
            countQuery = """
            select count(p) from PurchaseOrder p
            where p.storeId = :storeId
              and (:supplierId is null or p.supplierId = :supplierId)
              and (:status is null or p.status = :status)
              and (:q is null or p.poNumber like concat('%', :q, '%'))
            """)
    Page<PurchaseOrder> search(@Param("storeId") Long storeId, @Param("supplierId") Long supplierId,
                               @Param("status") PurchaseOrder.Status status, @Param("q") String q, Pageable pageable);
}
