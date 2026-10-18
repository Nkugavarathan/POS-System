package com.supermarket.pos.auth.service;

import com.supermarket.pos.audit.entity.AuditOutcome;
import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.auth.dto.AuthDtos.ApprovalGrantDto;
import com.supermarket.pos.auth.dto.AuthDtos.ApprovalRequest;
import com.supermarket.pos.auth.dto.AuthDtos.ApproverDto;
import com.supermarket.pos.auth.entity.ApprovalAction;
import com.supermarket.pos.auth.entity.ApprovalGrant;
import com.supermarket.pos.auth.repository.ApprovalGrantRepository;
import com.supermarket.pos.common.config.AppProperties;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.security.CurrentUser;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.users.entity.User;
import com.supermarket.pos.users.entity.UserStatus;
import com.supermarket.pos.users.service.UserAccountService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Manager approvals at the counter: the approver authenticates with username + PIN (or
 * password) on the cashier's screen and receives a single-use token scoped to
 * {action, store, requesting user, limit}. The token is consumed inside the business
 * transaction that needs it, so it cannot be replayed.
 */
@Service
public class ApprovalService {

    private final ApprovalGrantRepository grants;
    private final UserAccountService accounts;
    private final CurrentUserProvider currentUser;
    private final AppProperties properties;
    private final AuditService audit;
    private final Clock clock;

    public ApprovalService(ApprovalGrantRepository grants, UserAccountService accounts,
                           CurrentUserProvider currentUser, AppProperties properties, AuditService audit, Clock clock) {
        this.grants = grants;
        this.accounts = accounts;
        this.currentUser = currentUser;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    /** What a consumed approval proves. */
    public record ConsumedApproval(Long grantId, Long approverId, ApprovalAction action, BigDecimal limitValue) {
    }

    @Transactional(noRollbackFor = ApiException.class)
    public ApprovalGrantDto grant(ApprovalRequest request) {
        CurrentUser requester = currentUser.require();
        Long storeId = requester.requireActiveStore();
        Instant now = clock.instant();

        Optional<User> found = accounts.findForAuthentication(request.username());
        if (found.isEmpty()) {
            accounts.simulatePasswordCheck(request.secret());
            deny(request, null, storeId, "Unknown approver");
            throw approvalDenied();
        }
        User approver = found.get();
        if (approver.getStatus() != UserStatus.ACTIVE || approver.isTemporarilyLocked(now)) {
            deny(request, approver.getId(), storeId, "Approver account not active");
            throw approvalDenied();
        }
        if (!accounts.approvalSecretMatches(approver, request.secret())) {
            accounts.registerFailedLogin(approver);
            deny(request, approver.getId(), storeId, "Wrong approval secret");
            throw approvalDenied();
        }
        if (approver.getId().equals(requester.id())) {
            deny(request, approver.getId(), storeId, "Self-approval");
            throw new ApiException(ErrorCode.APPROVAL_INVALID, "A different person must approve this action.");
        }
        if (!approver.permissionCodes().contains(request.action().requiredPermission())) {
            deny(request, approver.getId(), storeId, "Approver lacks " + request.action().requiredPermission());
            throw new ApiException(ErrorCode.APPROVAL_INVALID, approver.getFullName()
                    + " is not allowed to approve this action.");
        }
        if (!approver.storeIds().contains(storeId)) {
            deny(request, approver.getId(), storeId, "Approver has no access to the store");
            throw new ApiException(ErrorCode.APPROVAL_INVALID, approver.getFullName()
                    + " cannot approve actions in this store.");
        }
        BigDecimal limit = switch (request.action().limitType()) {
            case NONE -> null;
            case REQUESTED_VALUE -> requireValue(request);
            case APPROVER_DISCOUNT_PERCENT -> approver.maxDiscountPercent();
        };
        if (request.action().limitType() == ApprovalAction.LimitType.APPROVER_DISCOUNT_PERCENT
                && request.value() != null && request.value().compareTo(limit) > 0) {
            deny(request, approver.getId(), storeId, "Above approver discount limit");
            throw new ApiException(ErrorCode.APPROVAL_INVALID, "The requested discount exceeds "
                    + approver.getFullName() + "'s limit of " + limit.stripTrailingZeros().toPlainString() + "%.");
        }

        String raw = SecureTokens.newToken("ag_");
        ApprovalGrant grant = new ApprovalGrant();
        grant.setTokenHash(SecureTokens.sha256(raw));
        grant.setAction(request.action());
        grant.setStoreId(storeId);
        grant.setRequestedBy(requester.id());
        grant.setApprovedBy(approver.getId());
        grant.setRegisterId(request.registerId());
        grant.setLimitValue(limit);
        grant.setIssuedAt(now);
        grant.setExpiresAt(now.plus(properties.security().approvalTokenTtl()));
        grants.save(grant);
        accounts.registerSuccessfulLogin(approver);

        Map<String, Object> details = new HashMap<>();
        details.put("action", request.action().name());
        details.put("approverId", approver.getId());
        if (request.value() != null) {
            details.put("value", request.value().toPlainString());
        }
        audit.record(AuditService.Entry.success("APPROVAL_GRANTED", "ApprovalGrant", grant.getId(),
                approver.getUsername() + " approved " + request.action() + " for " + requester.username())
                .withStore(storeId).withDetails(details));
        return new ApprovalGrantDto(raw, request.action(), new ApproverDto(approver.getId(), approver.getFullName()),
                limit, grant.getExpiresAt());
    }

    /**
     * Consumes an approval token inside the caller's transaction. Fails unless the token
     * exists, is unexpired and unused, and matches the action, store, requester and value.
     *
     * @param value the value the action needs (discount %, amount); null for unlimited actions
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ConsumedApproval consume(String rawToken, ApprovalAction action, Long storeId, Long requesterId,
                                    BigDecimal value) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(ErrorCode.APPROVAL_REQUIRED, "Manager approval is required for " + action + ".",
                    Map.of("action", action.name()));
        }
        Instant now = clock.instant();
        ApprovalGrant grant = grants.findByHashForUpdate(SecureTokens.sha256(rawToken))
                .orElseThrow(ApprovalService::invalidToken);
        boolean valid = grant.getConsumedAt() == null
                && grant.getExpiresAt().isAfter(now)
                && grant.getAction() == action
                && grant.getStoreId().equals(storeId)
                && grant.getRequestedBy().equals(requesterId);
        if (!valid) {
            throw invalidToken();
        }
        if (grant.getLimitValue() != null && value != null && value.compareTo(grant.getLimitValue()) > 0) {
            throw new ApiException(ErrorCode.APPROVAL_INVALID, "The approval does not cover this value.");
        }
        grant.setConsumedAt(now);
        return new ConsumedApproval(grant.getId(), grant.getApprovedBy(), action, grant.getLimitValue());
    }

    /** Records which document used the approval (called once the document has an id). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void linkReference(Long grantId, String referenceType, Long referenceId) {
        grants.findById(grantId).ifPresent(g -> {
            g.setConsumedRefType(referenceType);
            g.setConsumedRefId(referenceId);
        });
    }

    /**
     * Finds a token among several sent with one request (e.g. a checkout carrying discount
     * and price-override approvals) that matches the action, without consuming it.
     */
    @Transactional(readOnly = true)
    public Optional<String> findMatching(java.util.Collection<String> rawTokens, ApprovalAction action) {
        if (rawTokens == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        for (String raw : rawTokens) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            Optional<ApprovalGrant> grant = grants.findByTokenHash(SecureTokens.sha256(raw));
            if (grant.isPresent() && grant.get().getAction() == action && grant.get().getConsumedAt() == null
                    && grant.get().getExpiresAt().isAfter(now)) {
                return Optional.of(raw);
            }
        }
        return Optional.empty();
    }

    @Scheduled(cron = "0 45 3 * * *")
    @Transactional
    public void purgeOld() {
        grants.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(90)));
    }

    private void deny(ApprovalRequest request, Long approverId, Long storeId, String reason) {
        audit.record(new AuditService.Entry("APPROVAL_DENIED", "User", approverId, AuditOutcome.DENIED,
                reason + " (" + request.action() + ")", Map.of("approverUsername", request.username()),
                storeId, null, null));
    }

    private static BigDecimal requireValue(ApprovalRequest request) {
        if (request.value() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "A value is required to approve " + request.action());
        }
        return request.value();
    }

    private static ApiException approvalDenied() {
        return new ApiException(ErrorCode.APPROVAL_INVALID, "Approval failed: invalid approver credentials.");
    }

    private static ApiException invalidToken() {
        return new ApiException(ErrorCode.APPROVAL_INVALID, "The approval is invalid, expired or already used.");
    }
}
