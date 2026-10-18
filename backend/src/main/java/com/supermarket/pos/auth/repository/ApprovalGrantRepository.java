package com.supermarket.pos.auth.repository;

import com.supermarket.pos.auth.entity.ApprovalGrant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ApprovalGrantRepository extends JpaRepository<ApprovalGrant, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ApprovalGrant g where g.tokenHash = :hash")
    Optional<ApprovalGrant> findByHashForUpdate(@Param("hash") String hash);

    Optional<ApprovalGrant> findByTokenHash(String hash);

    @Modifying
    @Query("delete from ApprovalGrant g where g.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
