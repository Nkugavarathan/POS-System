package com.supermarket.pos.purchases.repository;

import com.supermarket.pos.purchases.entity.GoodsReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<GoodsReceipt> findWithItemsById(Long id);

    List<GoodsReceipt> findByPurchaseOrderIdOrderByIdAsc(Long purchaseOrderId);

    @Query(value = """
            select g from GoodsReceipt g
            where g.storeId = :storeId and (:supplierId is null or g.supplierId = :supplierId)
            """,
            countQuery = "select count(g) from GoodsReceipt g where g.storeId = :storeId and (:supplierId is null or g.supplierId = :supplierId)")
    Page<GoodsReceipt> search(@Param("storeId") Long storeId, @Param("supplierId") Long supplierId, Pageable pageable);
}
