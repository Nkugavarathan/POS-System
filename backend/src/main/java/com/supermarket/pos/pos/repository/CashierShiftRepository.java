package com.supermarket.pos.pos.repository;

import com.supermarket.pos.pos.entity.CashierShift;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface CashierShiftRepository extends JpaRepository<CashierShift, Long> {

    Optional<CashierShift> findFirstByCashierIdAndStatus(Long cashierId, CashierShift.Status status);

    Optional<CashierShift> findFirstByRegisterIdAndStatus(Long registerId, CashierShift.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CashierShift s where s.id = :id")
    Optional<CashierShift> lockById(@Param("id") Long id);

    @Query(value = """
            select s from CashierShift s
            where s.storeId = :storeId
              and (:cashierId is null or s.cashierId = :cashierId)
              and (:registerId is null or s.registerId = :registerId)
              and (:status is null or s.status = :status)
              and s.openedAt >= :from and s.openedAt < :to
            """,
            countQuery = """
            select count(s) from CashierShift s
            where s.storeId = :storeId
              and (:cashierId is null or s.cashierId = :cashierId)
              and (:registerId is null or s.registerId = :registerId)
              and (:status is null or s.status = :status)
              and s.openedAt >= :from and s.openedAt < :to
            """)
    Page<CashierShift> search(@Param("storeId") Long storeId, @Param("cashierId") Long cashierId,
                              @Param("registerId") Long registerId, @Param("status") CashierShift.Status status,
                              @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
