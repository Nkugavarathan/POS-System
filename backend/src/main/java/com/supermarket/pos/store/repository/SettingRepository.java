package com.supermarket.pos.store.repository;

import com.supermarket.pos.store.entity.Setting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SettingRepository extends JpaRepository<Setting, Long> {

    /** Global rows plus the store's overrides; callers prefer the store row. */
    @Query("select s from Setting s where s.storeId is null or s.storeId = :storeId")
    List<Setting> findEffective(@Param("storeId") Long storeId);

    Optional<Setting> findByStoreIdIsNullAndKey(String key);

    Optional<Setting> findByStoreIdAndKey(Long storeId, String key);
}
