package com.supermarket.pos.customers.repository;

import com.supermarket.pos.customers.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByPhone(String phone);

    Optional<Customer> findByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmailIgnoreCase(String email);

    @Query(value = """
            select c from Customer c
            where (:q is null or lower(c.fullName) like lower(concat('%', :q, '%'))
                   or c.phone like concat('%', :q, '%') or lower(c.email) like lower(concat('%', :q, '%'))
                   or c.customerCode = :q)
              and (:active is null or c.active = :active)
            """,
            countQuery = """
            select count(c) from Customer c
            where (:q is null or lower(c.fullName) like lower(concat('%', :q, '%'))
                   or c.phone like concat('%', :q, '%') or lower(c.email) like lower(concat('%', :q, '%'))
                   or c.customerCode = :q)
              and (:active is null or c.active = :active)
            """)
    Page<Customer> search(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);
}
