package com.supermarket.pos.store.repository;

import com.supermarket.pos.store.entity.TenderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenderTypeRepository extends JpaRepository<TenderType, String> {

    List<TenderType> findAllByOrderBySortOrderAsc();
}
