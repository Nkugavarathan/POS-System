package com.supermarket.pos.shop.repository;

import com.supermarket.pos.shop.entity.CustomerRefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface CustomerRefreshTokenRepository extends JpaRepository<CustomerRefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CustomerRefreshToken t where t.tokenHash = :hash")
    Optional<CustomerRefreshToken> findByHashForUpdate(@Param("hash") String hash);

    Optional<CustomerRefreshToken> findByTokenHash(String hash);

    @Modifying(flushAutomatically = true)
    @Query("update CustomerRefreshToken t set t.revokedAt = :now, t.revokeReason = :reason where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") String familyId, @Param("now") Instant now, @Param("reason") String reason);

    @Modifying(flushAutomatically = true)
    @Query("update CustomerRefreshToken t set t.revokedAt = :now, t.revokeReason = :reason where t.accountId = :accountId and t.revokedAt is null")
    int revokeAll(@Param("accountId") Long accountId, @Param("now") Instant now, @Param("reason") String reason);
}
