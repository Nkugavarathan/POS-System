package com.supermarket.pos.shop.service;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.security.JwtClaims;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** The signed-in online-shop customer (from a customer-type token only). */
@Component
public class CurrentCustomerProvider {

    public record CurrentCustomer(Long accountId, Long customerId, String fullName, String email) {
    }

    public Optional<CurrentCustomer> current() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)) {
            return Optional.empty();
        }
        Jwt jwt = token.getToken();
        if (!JwtClaims.TYPE_CUSTOMER.equals(jwt.getClaimAsString(JwtClaims.TOKEN_TYPE))) {
            return Optional.empty();
        }
        Object cid = jwt.getClaim(JwtClaims.CUSTOMER_ID);
        Long customerId = cid instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(cid));
        return Optional.of(new CurrentCustomer(Long.valueOf(jwt.getSubject()), customerId,
                jwt.getClaimAsString(JwtClaims.FULL_NAME), jwt.getClaimAsString(JwtClaims.USERNAME)));
    }

    public CurrentCustomer require() {
        return current().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Please sign in."));
    }
}
