package com.supermarket.pos.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Which stores a user may operate in. Stores are referenced by id (store module boundary). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_store_access")
public class UserStoreAccess {

    @EmbeddedId
    private UserStoreAccessId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    public UserStoreAccess(User user, Long storeId, boolean isDefault) {
        this.user = user;
        this.id = new UserStoreAccessId(user.getId(), storeId);
        this.isDefault = isDefault;
    }

    public Long getStoreId() {
        return id.getStoreId();
    }

    public boolean isDefault() {
        return isDefault;
    }
}
