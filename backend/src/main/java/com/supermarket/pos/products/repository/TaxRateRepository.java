package com.supermarket.pos.products.repository;

import com.supermarket.pos.products.entity.TaxRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {

    List<TaxRate> findAllByOrderByCodeAsc();

    boolean existsByCodeIgnoreCase(String code);
}
