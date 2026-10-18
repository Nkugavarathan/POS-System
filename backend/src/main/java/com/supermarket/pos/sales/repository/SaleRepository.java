package com.supermarket.pos.sales.repository;

import com.supermarket.pos.sales.entity.Sale;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Sale> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    Optional<Sale> findWithItemsByReceiptNumber(String receiptNumber);

    /** Serializes returns against the same sale. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> lockById(@Param("id") Long id);

    @Query(value = """
            select s from Sale s
            where s.storeId = :storeId
              and s.completedAt >= :from and s.completedAt < :to
              and (:cashierId is null or s.cashierId = :cashierId)
              and (:registerId is null or s.registerId = :registerId)
              and (:customerId is null or s.customerId = :customerId)
              and (:status is null or s.status = :status)
              and (:channel is null or s.channel = :channel)
              and (:receipt is null or s.receiptNumber like concat('%', :receipt, '%'))
            """,
            countQuery = """
            select count(s) from Sale s
            where s.storeId = :storeId
              and s.completedAt >= :from and s.completedAt < :to
              and (:cashierId is null or s.cashierId = :cashierId)
              and (:registerId is null or s.registerId = :registerId)
              and (:customerId is null or s.customerId = :customerId)
              and (:status is null or s.status = :status)
              and (:channel is null or s.channel = :channel)
              and (:receipt is null or s.receiptNumber like concat('%', :receipt, '%'))
            """)
    Page<Sale> search(@Param("storeId") Long storeId, @Param("from") Instant from, @Param("to") Instant to,
                      @Param("cashierId") Long cashierId, @Param("registerId") Long registerId,
                      @Param("customerId") Long customerId, @Param("status") Sale.Status status,
                      @Param("channel") Sale.Channel channel, @Param("receipt") String receipt, Pageable pageable);

    @Query("select count(s), coalesce(sum(s.grandTotal), 0) from Sale s where s.shiftId = :shiftId")
    List<Object[]> shiftTotals(@Param("shiftId") Long shiftId);

    Page<Sale> findByCustomerIdOrderByCompletedAtDesc(Long customerId, Pageable pageable);

    /** product id, number of sales containing it, first and last purchase: a customer's buying rhythm. */
    @Query("""
            select si.productId, count(distinct s.id), min(s.completedAt), max(s.completedAt)
            from SaleItem si join si.sale s
            where s.customerId = :customerId
            group by si.productId
            """)
    List<Object[]> productHistory(@Param("customerId") Long customerId);

    /** tender type, count, sum(amount) for all sales of a shift (X/Z reports, expected cash). */
    @Query("""
            select p.tenderType, count(p), sum(p.amount) from Payment p, Sale s
            where p.saleId = s.id and s.shiftId = :shiftId
            group by p.tenderType
            """)
    List<Object[]> tenderTotalsForShift(@Param("shiftId") Long shiftId);
}
