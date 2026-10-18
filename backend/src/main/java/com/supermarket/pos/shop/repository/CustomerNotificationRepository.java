package com.supermarket.pos.shop.repository;

import com.supermarket.pos.shop.entity.CustomerNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface CustomerNotificationRepository extends JpaRepository<CustomerNotification, Long> {

    Page<CustomerNotification> findByCustomerAccountIdOrderByIdDesc(Long accountId, Pageable pageable);

    long countByCustomerAccountIdAndReadAtIsNull(Long accountId);

    @Modifying
    @Query("update CustomerNotification n set n.readAt = :now where n.customerAccountId = :accountId and n.readAt is null")
    int markAllRead(@Param("accountId") Long accountId, @Param("now") Instant now);
}
