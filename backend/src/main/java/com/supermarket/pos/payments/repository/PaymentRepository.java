package com.supermarket.pos.payments.repository;

import com.supermarket.pos.payments.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findBySaleIdOrderByIdAsc(Long saleId);

    List<Payment> findBySaleIdIn(Collection<Long> saleIds);
}
