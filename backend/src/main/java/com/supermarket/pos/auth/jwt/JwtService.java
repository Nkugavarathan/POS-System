package com.supermarket.pos.auth.jwt;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.supermarket.pos.common.config.AppProperties;
import com.supermarket.pos.common.security.JwtClaims;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

/**
 * Issues and validates HS256 access tokens. The signing key comes from {@code JWT_SECRET}
 * (base64, at least 256 bits) and is never logged.
 */
@Component
public class JwtService {

    public static final String ISSUER = "pos-backend";

    private final SecretKey key;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AppProperties properties;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = new SecretKeySpec(decodeSecret(properties.security().jwtSecret()), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        nimbus.setJwtValidator(org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(ISSUER));
        this.decoder = nimbus;
    }

    /** The data the access token carries; see {@link JwtClaims}. */
    public record AccessTokenSubject(Long userId, String username, String fullName, Collection<String> roles,
                                     Collection<String> permissions, Collection<Long> storeIds,
                                     Long defaultStoreId, String sessionFamily, boolean mustChangePassword) {
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }

    public IssuedToken issueAccessToken(AccessTokenSubject subject) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.security().accessTokenTtl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(subject.userId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(JwtClaims.USERNAME, subject.username())
                .claim(JwtClaims.FULL_NAME, subject.fullName())
                .claim(JwtClaims.ROLES, List.copyOf(subject.roles()))
                .claim(JwtClaims.PERMISSIONS, List.copyOf(subject.permissions()))
                .claim(JwtClaims.STORES, List.copyOf(subject.storeIds()))
                .claim(JwtClaims.SESSION_FAMILY, subject.sessionFamily())
                .claim(JwtClaims.MUST_CHANGE_PASSWORD, subject.mustChangePassword())
                .claim(JwtClaims.TOKEN_TYPE, JwtClaims.TYPE_STAFF);
        if (subject.defaultStoreId() != null) {
            claims.claim(JwtClaims.DEFAULT_STORE, subject.defaultStoreId());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    /** Access token for an online-shop customer: no staff roles or permissions, ever. */
    public IssuedToken issueCustomerToken(Long accountId, Long customerId, String fullName, String email) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.security().accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(accountId))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(JwtClaims.TOKEN_TYPE, JwtClaims.TYPE_CUSTOMER)
                .claim(JwtClaims.CUSTOMER_ID, customerId)
                .claim(JwtClaims.FULL_NAME, fullName)
                .claim(JwtClaims.USERNAME, email)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    private static byte[] decodeSecret(String secret) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException notBase64) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 256 bits (32 bytes); "
                    + "generate one with: openssl rand -base64 48");
        }
        return bytes;
    }
}
