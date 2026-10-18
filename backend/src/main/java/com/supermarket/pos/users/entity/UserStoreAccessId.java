package com.supermarket.pos.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Composite key of {@link UserStoreAccess}. */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@Embeddable
public class UserStoreAccessId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "store_id")
    private Long storeId;

    public UserStoreAccessId(Long userId, Long storeId) {
        this.userId = userId;
        this.storeId = storeId;
    }
}
