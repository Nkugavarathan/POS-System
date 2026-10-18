package com.supermarket.pos.inventory.repository;

import com.supermarket.pos.inventory.entity.StockAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {

    Page<StockAdjustment> findByStoreId(Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Optional<StockAdjustment> findWithItemsById(Long id);
}
