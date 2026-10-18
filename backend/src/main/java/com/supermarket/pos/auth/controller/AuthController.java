package com.supermarket.pos.auth.controller;

import com.supermarket.pos.auth.dto.AuthDtos.AuthUserDto;
import com.supermarket.pos.auth.dto.AuthDtos.ChangePasswordRequest;
import com.supermarket.pos.auth.dto.AuthDtos.ClientInfo;
import com.supermarket.pos.auth.dto.AuthDtos.LoginRequest;
import com.supermarket.pos.auth.jwt.AuthCookies;
import com.supermarket.pos.auth.service.AuthService;
import com.supermarket.pos.auth.service.AuthService.SessionResult;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.error.ProblemFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session endpoints. Tokens travel only in HttpOnly cookies; response bodies never contain them.
 */
@Tag(name = "Auth", description = "Login, token refresh, logout and the current user")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final AuthCookies cookies;

    public AuthController(AuthService auth, AuthCookies cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @Operation(summary = "Issue the XSRF-TOKEN cookie (call once before the first POST)")
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Sign in; sets the POS_AT and POS_RT HttpOnly cookies")
    @PostMapping("/login")
    public ResponseEntity<AuthUserDto> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return withCookies(auth.login(request, client(http)));
    }

    @Operation(summary = "Rotate the refresh token and issue a new access token")
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest http) {
        String raw = cookies.read(http, AuthCookies.REFRESH_COOKIE).orElse(null);
        try {
            return withCookies(auth.refresh(raw, client(http)));
        } catch (ApiException ex) {
            if (ex.getCode() != ErrorCode.REFRESH_TOKEN_INVALID) {
                throw ex;
            }
            HttpHeaders headers = new HttpHeaders();
            cookies.clear(headers);
            return ResponseEntity.status(ex.getCode().status()).headers(headers)
                    .body(ProblemFactory.create(ex.getCode(), ex.getMessage(), http.getRequestURI()));
        }
    }

    @Operation(summary = "Sign out: revoke the session and clear the cookies")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        auth.logout(cookies.read(http, AuthCookies.REFRESH_COOKIE).orElse(null));
        HttpHeaders headers = new HttpHeaders();
        cookies.clear(headers);
        return ResponseEntity.noContent().headers(headers).build();
    }

    @GetMapping("/me")
    public AuthUserDto me() {
        return auth.me();
    }

    @Operation(summary = "Change the own password; other sessions are signed out")
    @PostMapping("/me/password")
    public ResponseEntity<AuthUserDto> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                      HttpServletRequest http) {
        return withCookies(auth.changePassword(request, client(http)));
    }

    private ResponseEntity<AuthUserDto> withCookies(SessionResult result) {
        HttpHeaders headers = new HttpHeaders();
        cookies.writeAccess(headers, result.accessToken());
        cookies.writeRefresh(headers, result.refreshToken());
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(result.user());
    }

    static ClientInfo client(HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
    }
}
