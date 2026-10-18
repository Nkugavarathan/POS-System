package com.supermarket.pos.inventory.repository;

import com.supermarket.pos.inventory.entity.MovementType;
import com.supermarket.pos.inventory.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query(value = """
            select m from StockMovement m
            where m.storeId = :storeId
              and (:productId is null or m.productId = :productId)
              and (:type is null or m.movementType = :type)
              and m.createdAt >= :from and m.createdAt < :to
            """,
            countQuery = """
            select count(m) from StockMovement m
            where m.storeId = :storeId
              and (:productId is null or m.productId = :productId)
              and (:type is null or m.movementType = :type)
              and m.createdAt >= :from and m.createdAt < :to
            """)
    Page<StockMovement> search(@Param("storeId") Long storeId, @Param("productId") Long productId,
                               @Param("type") MovementType type, @Param("from") Instant from,
                               @Param("to") Instant to, Pageable pageable);

    List<StockMovement> findByReferenceTypeAndReferenceIdOrderByIdAsc(String referenceType, Long referenceId);

    List<StockMovement> findTop20ByStoreIdAndProductIdOrderByIdDesc(Long storeId, Long productId);
}
