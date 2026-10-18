package com.supermarket.pos.pos.repository;

import com.supermarket.pos.pos.entity.TransactionVoid;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionVoidRepository extends JpaRepository<TransactionVoid, Long> {

    long countByShiftId(Long shiftId);
}
