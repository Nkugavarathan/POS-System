package com.supermarket.pos.users.mapper;

import com.supermarket.pos.users.dto.UserDtos.PermissionDto;
import com.supermarket.pos.users.dto.UserDtos.RoleDto;
import com.supermarket.pos.users.dto.UserDtos.StoreRef;
import com.supermarket.pos.users.dto.UserDtos.UserDetailDto;
import com.supermarket.pos.users.dto.UserDtos.UserSummaryDto;
import com.supermarket.pos.users.entity.Permission;
import com.supermarket.pos.users.entity.Role;
import com.supermarket.pos.users.entity.User;
import com.supermarket.pos.users.entity.UserStoreAccess;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Explicit entity → DTO mapping. Password and PIN hashes are never mapped. */
public final class UserMapper {

    private UserMapper() {
    }

    /** Minimal store info supplied by the caller (store module). */
    public record StoreInfo(String code, String name) {
    }

    public static UserSummaryDto toSummary(User user) {
        return new UserSummaryDto(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getStatus(), sortedRoles(user), user.storeIds().stream().sorted().toList(),
                user.getLastLoginAt(), user.getCreatedAt());
    }

    public static UserDetailDto toDetail(User user, Map<Long, StoreInfo> stores, Instant now) {
        List<StoreRef> storeRefs = user.getStoreAccess().stream()
                .sorted(Comparator.comparing(UserStoreAccess::getStoreId))
                .map(a -> {
                    StoreInfo info = stores.get(a.getStoreId());
                    return new StoreRef(a.getStoreId(), info == null ? null : info.code(),
                            info == null ? null : info.name(), a.isDefault());
                })
                .toList();
        return new UserDetailDto(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getStatus(), sortedRoles(user), storeRefs, user.getPinHash() != null,
                user.isMustChangePassword(), user.isTemporarilyLocked(now), user.getLastLoginAt(),
                user.getCreatedAt(), user.getUpdatedAt(), user.getVersion());
    }

    public static RoleDto toRole(Role role) {
        return new RoleDto(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                role.getMaxDiscountPercent(), role.isSystemRole(),
                role.getPermissions().stream().map(Permission::getCode).sorted().toList());
    }

    public static PermissionDto toPermission(Permission permission) {
        return new PermissionDto(permission.getCode(), permission.getModule(), permission.getDescription());
    }

    private static List<String> sortedRoles(User user) {
        return user.getRoles().stream().map(Role::getCode).sorted().toList();
    }
}
