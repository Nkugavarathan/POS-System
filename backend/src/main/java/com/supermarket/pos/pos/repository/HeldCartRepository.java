package com.supermarket.pos.pos.repository;

import com.supermarket.pos.pos.entity.HeldCart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HeldCartRepository extends JpaRepository<HeldCart, Long> {

    @EntityGraph(attributePaths = "items")
    List<HeldCart> findByStoreIdAndStatusOrderByHeldAtDesc(Long storeId, HeldCart.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HeldCart h where h.id = :id")
    Optional<HeldCart> lockById(@Param("id") Long id);

    List<HeldCart> findByShiftIdAndStatus(Long shiftId, HeldCart.Status status);

    List<HeldCart> findByStatusAndHeldAtBefore(HeldCart.Status status, Instant cutoff);
}
