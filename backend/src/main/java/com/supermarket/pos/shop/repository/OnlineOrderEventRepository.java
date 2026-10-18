package com.supermarket.pos.shop.repository;

import com.supermarket.pos.shop.entity.OnlineOrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OnlineOrderEventRepository extends JpaRepository<OnlineOrderEvent, Long> {

    List<OnlineOrderEvent> findByOnlineOrderIdOrderByIdAsc(Long orderId);
}
