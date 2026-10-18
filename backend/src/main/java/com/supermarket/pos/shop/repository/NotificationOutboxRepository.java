package com.supermarket.pos.shop.repository;

import com.supermarket.pos.shop.entity.NotificationOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    @Query("select o from NotificationOutbox o where o.status = 'PENDING' and o.attempts < 5 order by o.id")
    List<NotificationOutbox> findPending(Pageable pageable);
}
