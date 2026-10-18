package com.supermarket.pos.pos.repository;

import com.supermarket.pos.pos.entity.CashMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {

    List<CashMovement> findByShiftIdOrderByIdAsc(Long shiftId);
}
