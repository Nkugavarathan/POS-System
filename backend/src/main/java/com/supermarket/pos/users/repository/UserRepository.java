package com.supermarket.pos.users.repository;

import com.supermarket.pos.users.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Loads everything needed to build the security principal in one round trip. */
    @EntityGraph(attributePaths = {"roles", "roles.permissions", "storeAccess"})
    Optional<User> findWithAuthoritiesByUsernameIgnoreCase(String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions", "storeAccess"})
    Optional<User> findWithAuthoritiesById(Long id);

    boolean existsByUsernameIgnoreCase(String username);

    /**
     * Atomic failed-login counter (no lost updates under concurrent attempts). MySQL/MariaDB
     * evaluate single-table SET clauses left to right, so lockedUntil is computed from the old count.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update User u
               set u.lockedUntil = case when u.failedLoginCount + 1 >= :maxFailures then :lockUntil
                                        else u.lockedUntil end,
                   u.failedLoginCount = u.failedLoginCount + 1
             where u.id = :id
            """)
    int registerFailedLogin(@Param("id") Long id, @Param("maxFailures") int maxFailures,
                            @Param("lockUntil") java.time.Instant lockUntil);

    @Modifying(flushAutomatically = true)
    @Query("""
            update User u set u.failedLoginCount = 0, u.lockedUntil = null, u.lastLoginAt = :now
             where u.id = :id
            """)
    int registerSuccessfulLogin(@Param("id") Long id, @Param("now") java.time.Instant now);

    boolean existsByEmailIgnoreCase(String email);

    @Query(value = """
            select distinct u from User u
            left join u.roles r
            where (:q is null or lower(u.username) like lower(concat('%', :q, '%'))
                              or lower(u.fullName) like lower(concat('%', :q, '%')))
              and (:roleCode is null or r.code = :roleCode)
              and (:status is null or u.status = :status)
            """,
            countQuery = """
            select count(distinct u) from User u
            left join u.roles r
            where (:q is null or lower(u.username) like lower(concat('%', :q, '%'))
                              or lower(u.fullName) like lower(concat('%', :q, '%')))
              and (:roleCode is null or r.code = :roleCode)
              and (:status is null or u.status = :status)
            """)
    Page<User> search(@Param("q") String q,
                      @Param("roleCode") String roleCode,
                      @Param("status") com.supermarket.pos.users.entity.UserStatus status,
                      Pageable pageable);
}
