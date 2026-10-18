package com.supermarket.pos.users.controller;

import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.users.dto.UserDtos.CreateUserRequest;
import com.supermarket.pos.users.dto.UserDtos.PasswordResetRequest;
import com.supermarket.pos.users.dto.UserDtos.PermissionDto;
import com.supermarket.pos.users.dto.UserDtos.RoleDto;
import com.supermarket.pos.users.dto.UserDtos.SetPinRequest;
import com.supermarket.pos.users.dto.UserDtos.UpdateRoleRequest;
import com.supermarket.pos.users.dto.UserDtos.UpdateUserRequest;
import com.supermarket.pos.users.dto.UserDtos.UserDetailDto;
import com.supermarket.pos.users.dto.UserDtos.UserStatusRequest;
import com.supermarket.pos.users.dto.UserDtos.UserSummaryDto;
import com.supermarket.pos.users.entity.UserStatus;
import com.supermarket.pos.users.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Users", description = "User, role and permission administration")
@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/users")
    public PageResponse<UserSummaryDto> search(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) String role,
                                               @RequestParam(required = false) UserStatus status,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @RequestParam(required = false) List<String> sort) {
        return users.search(q, role, status, page, size, sort);
    }

    @GetMapping("/users/{id}")
    public UserDetailDto get(@PathVariable Long id) {
        return users.get(id);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDetailDto create(@Valid @RequestBody CreateUserRequest request) {
        return users.create(request);
    }

    @PutMapping("/users/{id}")
    public UserDetailDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return users.update(id, request);
    }

    @PatchMapping("/users/{id}/status")
    public UserDetailDto setStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        return users.setStatus(id, request.status(), request.version());
    }

    @PostMapping("/users/{id}/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequest request) {
        users.resetPassword(id, request.temporaryPassword());
    }

    @PutMapping("/users/{id}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPin(@PathVariable Long id, @Valid @RequestBody SetPinRequest request) {
        users.setPin(id, request.pin());
    }

    @GetMapping("/roles")
    public List<RoleDto> roles() {
        return users.roles();
    }

    @PutMapping("/roles/{id}")
    public RoleDto updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return users.updateRole(id, request);
    }

    @GetMapping("/permissions")
    public List<PermissionDto> permissions() {
        return users.permissions();
    }
}
