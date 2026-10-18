package com.supermarket.pos.store.repository;

import com.supermarket.pos.store.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByCodeIgnoreCase(String code);

    List<Store> findByIdInOrderByNameAsc(Collection<Long> ids);
}
