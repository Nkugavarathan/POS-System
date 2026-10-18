package com.supermarket.pos.customers.repository;

import com.supermarket.pos.customers.entity.LoyaltyAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, Long> {

    Optional<LoyaltyAccount> findByCustomerId(Long customerId);

    Optional<LoyaltyAccount> findByCardNumber(String cardNumber);

    List<LoyaltyAccount> findByCustomerIdIn(Collection<Long> customerIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LoyaltyAccount a where a.customerId = :customerId")
    Optional<LoyaltyAccount> lockByCustomerId(@Param("customerId") Long customerId);
}
