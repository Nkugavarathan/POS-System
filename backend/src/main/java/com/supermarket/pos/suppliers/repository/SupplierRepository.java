package com.supermarket.pos.suppliers.repository;

import com.supermarket.pos.suppliers.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    boolean existsByCodeIgnoreCase(String code);

    @Query(value = """
            select s from Supplier s
            where (:q is null or lower(s.name) like lower(concat('%', :q, '%')) or lower(s.code) like lower(concat(:q, '%')))
              and (:active is null or s.active = :active)
            """,
            countQuery = """
            select count(s) from Supplier s
            where (:q is null or lower(s.name) like lower(concat('%', :q, '%')) or lower(s.code) like lower(concat(:q, '%')))
              and (:active is null or s.active = :active)
            """)
    Page<Supplier> search(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);
}
