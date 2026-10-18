package com.supermarket.pos.inventory.repository;

import com.supermarket.pos.inventory.entity.StockCount;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockCountRepository extends JpaRepository<StockCount, Long> {

    Page<StockCount> findByStoreId(Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Optional<StockCount> findWithItemsById(Long id);

    /** Serializes submit/post on the same count. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from StockCount c where c.id = :id")
    Optional<StockCount> lockById(@Param("id") Long id);
}
