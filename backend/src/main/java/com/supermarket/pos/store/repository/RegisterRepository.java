package com.supermarket.pos.store.repository;

import com.supermarket.pos.store.entity.Register;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegisterRepository extends JpaRepository<Register, Long> {

    List<Register> findByStoreIdOrderByCodeAsc(Long storeId);

    Optional<Register> findByIdAndStoreId(Long id, Long storeId);

    boolean existsByStoreIdAndCodeIgnoreCase(Long storeId, String code);
}
