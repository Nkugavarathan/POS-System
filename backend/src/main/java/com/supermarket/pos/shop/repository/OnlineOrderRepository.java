package com.supermarket.pos.shop.repository;

import com.supermarket.pos.shop.entity.OnlineOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OnlineOrderRepository extends JpaRepository<OnlineOrder, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<OnlineOrder> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    Optional<OnlineOrder> findWithItemsByOrderNumber(String orderNumber);

    /** Every status change locks the order row: staff, customer and scheduler never race. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OnlineOrder o where o.id = :id")
    Optional<OnlineOrder> lockById(@Param("id") Long id);

    Page<OnlineOrder> findByCustomerAccountIdOrderByPlacedAtDesc(Long accountId, Pageable pageable);

    @Query(value = """
            select o from OnlineOrder o
            where o.storeId = :storeId
              and (:statusesEmpty = true or o.status in :statuses)
              and (:q is null or o.orderNumber like concat('%', :q, '%') or o.pickupCode = :q)
            """,
            countQuery = """
            select count(o) from OnlineOrder o
            where o.storeId = :storeId
              and (:statusesEmpty = true or o.status in :statuses)
              and (:q is null or o.orderNumber like concat('%', :q, '%') or o.pickupCode = :q)
            """)
    Page<OnlineOrder> search(@Param("storeId") Long storeId, @Param("statusesEmpty") boolean statusesEmpty,
                             @Param("statuses") Collection<OnlineOrder.Status> statuses, @Param("q") String q,
                             Pageable pageable);

    @Query("select o.status, count(o) from OnlineOrder o where o.storeId = :storeId and o.status not in :closed group by o.status")
    List<Object[]> openCounts(@Param("storeId") Long storeId, @Param("closed") Collection<OnlineOrder.Status> closed);

    @Query("select count(o) from OnlineOrder o where o.storeId = :storeId and o.status = :status and o.arrivedAt is not null")
    long countArrived(@Param("storeId") Long storeId, @Param("status") OnlineOrder.Status status);

    List<OnlineOrder> findByStatusAndCollectByBefore(OnlineOrder.Status status, Instant cutoff);

    Optional<OnlineOrder> findFirstByStoreIdAndPickupCodeAndStatus(Long storeId, String pickupCode, OnlineOrder.Status status);
}
