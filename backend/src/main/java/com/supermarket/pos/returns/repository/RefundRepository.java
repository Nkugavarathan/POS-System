package com.supermarket.pos.returns.repository;

import com.supermarket.pos.returns.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByReturnIdOrderByIdAsc(Long returnId);
}
