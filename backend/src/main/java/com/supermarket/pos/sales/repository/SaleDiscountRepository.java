package com.supermarket.pos.sales.repository;

import com.supermarket.pos.sales.entity.SaleDiscount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SaleDiscountRepository extends JpaRepository<SaleDiscount, Long> {

    List<SaleDiscount> findBySaleId(Long saleId);
}
