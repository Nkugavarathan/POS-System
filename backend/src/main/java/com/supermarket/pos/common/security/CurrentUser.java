package com.supermarket.pos.common.security;

import com.supermarket.pos.common.error.ApiException;

import java.util.Set;

/**
 * The authenticated caller, built from the validated access token. {@code activeStoreId} is
 * the store this request operates on (X-Store-Id header, otherwise the user's default store).
 */
public record CurrentUser(Long id,
                          String username,
                          String fullName,
                          Set<String> roles,
                          Set<String> permissions,
                          Set<Long> storeIds,
                          Long activeStoreId) {

    public boolean has(String permission) {
        return permissions.contains(permission);
    }

    public boolean hasAny(String... candidates) {
        for (String p : candidates) {
            if (permissions.contains(p)) {
                return true;
            }
        }
        return false;
    }

    public boolean canAccessStore(Long storeId) {
        return storeId != null && storeIds.contains(storeId);
    }

    /** The active store; fails if the user has no store at all. */
    public Long requireActiveStore() {
        if (activeStoreId == null) {
            throw ApiException.forbidden("Your account is not assigned to any store.");
        }
        return activeStoreId;
    }

    /**
     * Guards against insecure direct object references: a record that belongs to a store the
     * user cannot access is reported as "not found", so its existence is not revealed.
     */
    public void assertStoreAccess(Long storeId, String entity, Object id) {
        if (!canAccessStore(storeId)) {
            throw ApiException.notFound(entity, id);
        }
    }
}
