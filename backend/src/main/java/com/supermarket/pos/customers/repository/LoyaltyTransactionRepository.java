package com.supermarket.pos.customers.repository;

import com.supermarket.pos.customers.entity.LoyaltyTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {

    Page<LoyaltyTransaction> findByLoyaltyAccountId(Long accountId, Pageable pageable);

    List<LoyaltyTransaction> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);
}
