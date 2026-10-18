package com.supermarket.pos.common.security;

/** Claim names shared by the token issuer (auth module) and {@link CurrentUserProvider}. */
public final class JwtClaims {

    public static final String USERNAME = "username";
    public static final String FULL_NAME = "name";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "perms";
    public static final String STORES = "stores";
    public static final String DEFAULT_STORE = "dstore";
    public static final String SESSION_FAMILY = "sid";
    public static final String MUST_CHANGE_PASSWORD = "mcp";

    private JwtClaims() {
    }
}
