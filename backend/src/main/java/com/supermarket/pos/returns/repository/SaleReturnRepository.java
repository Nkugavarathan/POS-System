package com.supermarket.pos.returns.repository;

import com.supermarket.pos.returns.entity.SaleReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<SaleReturn> findWithItemsById(Long id);

    List<SaleReturn> findByOriginalSaleIdOrderByIdAsc(Long saleId);

    @Query(value = """
            select r from SaleReturn r
            where r.storeId = :storeId and r.createdAt >= :from and r.createdAt < :to
              and (:saleId is null or r.originalSaleId = :saleId)
            """,
            countQuery = """
            select count(r) from SaleReturn r
            where r.storeId = :storeId and r.createdAt >= :from and r.createdAt < :to
              and (:saleId is null or r.originalSaleId = :saleId)
            """)
    Page<SaleReturn> search(@Param("storeId") Long storeId, @Param("from") Instant from, @Param("to") Instant to,
                            @Param("saleId") Long saleId, Pageable pageable);

    @Query("select count(r), coalesce(sum(r.refundTotal), 0) from SaleReturn r where r.shiftId = :shiftId")
    List<Object[]> shiftTotals(@Param("shiftId") Long shiftId);

    @Query("""
            select f.tenderType, count(f), sum(f.amount) from Refund f, SaleReturn r
            where f.returnId = r.id and r.shiftId = :shiftId group by f.tenderType
            """)
    List<Object[]> refundTendersForShift(@Param("shiftId") Long shiftId);

    @Query("""
            select f.tenderType, sum(f.amount) from Refund f, SaleReturn r
            where f.returnId = r.id and r.originalSaleId = :saleId group by f.tenderType
            """)
    List<Object[]> refundedByTender(@Param("saleId") Long saleId);
}
