package com.supermarket.pos.users.service;

import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.security.CurrentUser;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.common.web.PageRequests;
import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.store.dto.StoreDtos.StoreSummaryDto;
import com.supermarket.pos.store.service.StoreService;
import com.supermarket.pos.users.dto.UserDtos.CreateUserRequest;
import com.supermarket.pos.users.dto.UserDtos.PermissionDto;
import com.supermarket.pos.users.dto.UserDtos.RoleDto;
import com.supermarket.pos.users.dto.UserDtos.UpdateRoleRequest;
import com.supermarket.pos.users.dto.UserDtos.UpdateUserRequest;
import com.supermarket.pos.users.dto.UserDtos.UserDetailDto;
import com.supermarket.pos.users.dto.UserDtos.UserSummaryDto;
import com.supermarket.pos.users.entity.Permission;
import com.supermarket.pos.users.entity.Role;
import com.supermarket.pos.users.entity.User;
import com.supermarket.pos.users.entity.UserStatus;
import com.supermarket.pos.users.entity.UserStoreAccess;
import com.supermarket.pos.users.mapper.UserMapper;
import com.supermarket.pos.users.repository.PermissionRepository;
import com.supermarket.pos.users.repository.RoleRepository;
import com.supermarket.pos.users.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User and role administration. Least-privilege rules enforced here (not only in the UI):
 * only a SUPER_ADMIN may grant or edit SUPER_ADMIN; administrators can only assign stores
 * they can access themselves; nobody can disable their own account.
 */
@Service
public class UserService {

    private static final Map<String, String> SORTS = Map.of(
            "username", "username", "fullName", "fullName", "createdAt", "createdAt", "lastLoginAt", "lastLoginAt");

    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final StoreService stores;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserService(UserRepository users, RoleRepository roles, PermissionRepository permissions,
                       StoreService stores, PasswordEncoder passwordEncoder, CurrentUserProvider currentUser,
                       AuditService audit, ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.stores = stores;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('USER_VIEW')")
    @Transactional(readOnly = true)
    public PageResponse<UserSummaryDto> search(String q, String role, UserStatus status, Integer page, Integer size,
                                               List<String> sort) {
        var pageable = PageRequests.of(page, size, sort, SORTS, Sort.by("username"));
        String query = q == null || q.isBlank() ? null : q.trim();
        return PageResponse.of(users.search(query, role, status, pageable), UserMapper::toSummary);
    }

    @PreAuthorize("hasAuthority('USER_VIEW')")
    @Transactional(readOnly = true)
    public UserDetailDto get(Long id) {
        return toDetail(load(id));
    }

    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public UserDetailDto create(CreateUserRequest request) {
        CurrentUser actor = currentUser.require();
        String username = request.username().trim();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw ApiException.duplicate("username", "Username " + username + " is already taken.");
        }
        if (request.email() != null && !request.email().isBlank() && users.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.duplicate("email", "Email is already used by another account.");
        }
        User user = new User();
        user.setUsername(username);
        user.setFullName(request.fullName().trim());
        user.setEmail(blankToNull(request.email()));
        user.setPasswordHash(passwordEncoder.encode(request.temporaryPassword()));
        user.setMustChangePassword(true);
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(resolveRoles(actor, request.roles()));
        users.saveAndFlush(user);
        applyStores(actor, user, request.storeIds(), request.defaultStoreId());
        users.saveAndFlush(user);
        audit.record(AuditService.Entry.success("USER_CREATED", "User", user.getId(), "User " + username + " created")
                .withDetails(Map.of("roles", request.roles(), "stores", request.storeIds())));
        return toDetail(user);
    }

    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public UserDetailDto update(Long id, UpdateUserRequest request) {
        CurrentUser actor = currentUser.require();
        User user = load(id);
        checkVersion(user, request.version());
        guardSuperAdminTarget(actor, user);
        if (request.email() != null && !request.email().isBlank()
                && !request.email().equalsIgnoreCase(user.getEmail()) && users.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.duplicate("email", "Email is already used by another account.");
        }
        Set<String> before = user.roleCodes();
        user.setFullName(request.fullName().trim());
        user.setEmail(blankToNull(request.email()));
        user.setRoles(resolveRoles(actor, request.roles()));
        applyStores(actor, user, request.storeIds(), request.defaultStoreId());
        users.saveAndFlush(user);
        boolean rolesChanged = !before.equals(user.roleCodes());
        if (rolesChanged) {
            // New permissions must not wait for the old access token to expire on the next refresh.
            events.publishEvent(new UserEvents.SessionsInvalidated(id, "ROLES_CHANGED"));
        }
        audit.record(AuditService.Entry.success("USER_UPDATED", "User", id, "User " + user.getUsername() + " updated")
                .withDetails(Map.of("rolesBefore", before, "rolesAfter", user.roleCodes())));
        return toDetail(user);
    }

    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public UserDetailDto setStatus(Long id, UserStatus status, Long version) {
        CurrentUser actor = currentUser.require();
        User user = load(id);
        checkVersion(user, version);
        guardSuperAdminTarget(actor, user);
        if (actor.id().equals(id) && status != UserStatus.ACTIVE) {
            throw ApiException.rule("You cannot disable or lock your own account.");
        }
        UserStatus old = user.getStatus();
        user.setStatus(status);
        if (status == UserStatus.ACTIVE) {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
        } else {
            events.publishEvent(new UserEvents.SessionsInvalidated(id, "USER_" + status.name()));
        }
        users.saveAndFlush(user);
        audit.record(AuditService.Entry.success("USER_STATUS_CHANGED", "User", id,
                user.getUsername() + ": " + old + " -> " + status));
        return toDetail(user);
    }

    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public void resetPassword(Long id, String temporaryPassword) {
        CurrentUser actor = currentUser.require();
        User user = load(id);
        guardSuperAdminTarget(actor, user);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(clock.instant());
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        events.publishEvent(new UserEvents.SessionsInvalidated(id, "PASSWORD_RESET"));
        audit.record(AuditService.Entry.success("USER_PASSWORD_RESET", "User", id,
                "Temporary password set for " + user.getUsername()));
    }

    /** Admins may set anyone's PIN (except SUPER_ADMIN unless they are one); users may set their own. */
    @Transactional
    public void setPin(Long id, String pin) {
        CurrentUser actor = currentUser.require();
        if (!actor.id().equals(id) && !actor.has("USER_MANAGE")) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "You can only change your own PIN.");
        }
        User user = load(id);
        if (!actor.id().equals(id)) {
            guardSuperAdminTarget(actor, user);
        }
        user.setPinHash(passwordEncoder.encode(pin));
        audit.record(AuditService.Entry.success("USER_PIN_SET", "User", id, "Approval PIN set for " + user.getUsername()));
    }

    // ----- roles -----------------------------------------------------------------------------

    @PreAuthorize("hasAnyAuthority('USER_VIEW','ROLE_MANAGE')")
    @Transactional(readOnly = true)
    public List<RoleDto> roles() {
        return roles.findAllByOrderByIdAsc().stream().map(UserMapper::toRole).toList();
    }

    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Transactional(readOnly = true)
    public List<PermissionDto> permissions() {
        return permissions.findAllByOrderByModuleAscCodeAsc().stream().map(UserMapper::toPermission).toList();
    }

    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Transactional
    public RoleDto updateRole(Long roleId, UpdateRoleRequest request) {
        Role role = roles.findWithPermissionsById(roleId).orElseThrow(() -> ApiException.notFound("Role", roleId));
        List<Permission> found = permissions.findByCodeIn(request.permissions());
        if (found.size() != request.permissions().size()) {
            Set<String> known = found.stream().map(Permission::getCode).collect(Collectors.toSet());
            Set<String> unknown = new HashSet<>(request.permissions());
            unknown.removeAll(known);
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown permissions: " + unknown);
        }
        if (Role.SUPER_ADMIN.equals(role.getCode()) && found.size() != permissions.count()) {
            throw ApiException.rule("SUPER_ADMIN always holds every permission.");
        }
        Set<String> before = role.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet());
        role.setPermissions(new HashSet<>(found));
        role.setMaxDiscountPercent(request.maxDiscountPercent());
        audit.record(AuditService.Entry.success("ROLE_UPDATED", "Role", roleId, "Role " + role.getCode() + " updated")
                .withDetails(Map.of("before", before, "after", request.permissions(),
                        "maxDiscountPercent", request.maxDiscountPercent())));
        // Holders pick the change up at their next refresh (access tokens live 10 minutes).
        return UserMapper.toRole(roles.saveAndFlush(role));
    }

    // ----- helpers ---------------------------------------------------------------------------

    private User load(Long id) {
        return users.findWithAuthoritiesById(id).orElseThrow(() -> ApiException.notFound("User", id));
    }

    private UserDetailDto toDetail(User user) {
        Map<Long, StoreSummaryDto> summaries = stores.summaries(user.storeIds());
        Map<Long, UserMapper.StoreInfo> info = summaries.values().stream()
                .collect(Collectors.toMap(StoreSummaryDto::id, s -> new UserMapper.StoreInfo(s.code(), s.name())));
        return UserMapper.toDetail(user, info, clock.instant());
    }

    private Set<Role> resolveRoles(CurrentUser actor, Set<String> codes) {
        List<Role> found = roles.findByCodeIn(codes);
        if (found.size() != codes.size()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown role in " + codes);
        }
        if (codes.contains(Role.SUPER_ADMIN) && !actor.roles().contains(Role.SUPER_ADMIN)) {
            throw ApiException.forbidden("Only a super administrator can grant SUPER_ADMIN.");
        }
        return new HashSet<>(found);
    }

    private void applyStores(CurrentUser actor, User user, Set<Long> storeIds, Long defaultStoreId) {
        boolean superAdmin = actor.roles().contains(Role.SUPER_ADMIN);
        Map<Long, StoreSummaryDto> existing = stores.summaries(storeIds);
        for (Long storeId : storeIds) {
            if (!existing.containsKey(storeId)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown store " + storeId);
            }
            if (!superAdmin && !actor.canAccessStore(storeId)) {
                throw ApiException.forbidden("You can only assign stores you have access to.");
            }
        }
        Long effectiveDefault = defaultStoreId != null && storeIds.contains(defaultStoreId)
                ? defaultStoreId : storeIds.stream().sorted().findFirst().orElseThrow();
        user.getStoreAccess().removeIf(a -> !storeIds.contains(a.getStoreId()));
        for (Long storeId : storeIds) {
            user.getStoreAccess().stream().filter(a -> a.getStoreId().equals(storeId)).findFirst()
                    .ifPresentOrElse(a -> a.setDefault(storeId.equals(effectiveDefault)),
                            () -> user.getStoreAccess().add(
                                    new UserStoreAccess(user, storeId, storeId.equals(effectiveDefault))));
        }
    }

    private static void guardSuperAdminTarget(CurrentUser actor, User target) {
        if (target.roleCodes().contains(Role.SUPER_ADMIN) && !actor.roles().contains(Role.SUPER_ADMIN)) {
            throw ApiException.forbidden("Only a super administrator can change a super administrator account.");
        }
    }

    private static void checkVersion(User user, Long expected) {
        if (expected == null || user.getVersion() != expected) {
            throw new OptimisticLockingFailureException("User version mismatch");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
