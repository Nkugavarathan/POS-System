package com.supermarket.pos.shop.repository;

import com.supermarket.pos.shop.entity.CustomerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, Long> {

    Optional<CustomerAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByCustomerId(Long customerId);

    Optional<CustomerAccount> findByCustomerId(Long customerId);
}
