package com.supermarket.pos.shop.service;

import com.supermarket.pos.audit.entity.AuditOutcome;
import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.auth.jwt.JwtService;
import com.supermarket.pos.auth.service.SecureTokens;
import com.supermarket.pos.common.config.AppProperties;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.customers.entity.Customer;
import com.supermarket.pos.customers.entity.LoyaltyAccount;
import com.supermarket.pos.customers.repository.CustomerRepository;
import com.supermarket.pos.customers.repository.LoyaltyAccountRepository;
import com.supermarket.pos.customers.service.CustomerService;
import com.supermarket.pos.customers.service.LoyaltyService;
import com.supermarket.pos.customers.service.PhoneNumbers;
import com.supermarket.pos.shop.dto.ShopDtos.LinkCardRequest;
import com.supermarket.pos.shop.dto.ShopDtos.RegisterRequest;
import com.supermarket.pos.shop.dto.ShopDtos.ShopLoginRequest;
import com.supermarket.pos.shop.dto.ShopDtos.ShopMeDto;
import com.supermarket.pos.shop.entity.CustomerAccount;
import com.supermarket.pos.shop.entity.CustomerRefreshToken;
import com.supermarket.pos.shop.repository.CustomerAccountRepository;
import com.supermarket.pos.shop.repository.CustomerRefreshTokenRepository;
import com.supermarket.pos.shop.service.CurrentCustomerProvider.CurrentCustomer;
import com.supermarket.pos.store.service.StoreService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Online-shop accounts and sessions. Same protections as staff sign-in: BCrypt, generic errors,
 * temporary lockout, rotating refresh tokens with reuse detection, separate cookies.
 */
@Service
public class CustomerAuthService {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);
    private static final Duration ROTATION_GRACE = Duration.ofSeconds(20);

    private final CustomerAccountRepository accounts;
    private final CustomerRefreshTokenRepository tokens;
    private final CustomerRepository customerRepository;
    private final LoyaltyAccountRepository loyaltyAccounts;
    private final CustomerService customers;
    private final LoyaltyService loyalty;
    private final NotificationService notifications;
    private final StoreService stores;
    private final JwtService jwt;
    private final PasswordEncoder passwordEncoder;
    private final CurrentCustomerProvider currentCustomer;
    private final AppProperties properties;
    private final AuditService audit;
    private final Clock clock;
    private final String dummyHash;

    public CustomerAuthService(CustomerAccountRepository accounts, CustomerRefreshTokenRepository tokens,
                               CustomerRepository customerRepository, LoyaltyAccountRepository loyaltyAccounts,
                               CustomerService customers, LoyaltyService loyalty, NotificationService notifications,
                               StoreService stores, JwtService jwt, PasswordEncoder passwordEncoder,
                               CurrentCustomerProvider currentCustomer, AppProperties properties, AuditService audit,
                               Clock clock) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.customerRepository = customerRepository;
        this.loyaltyAccounts = loyaltyAccounts;
        this.customers = customers;
        this.loyalty = loyalty;
        this.notifications = notifications;
        this.stores = stores;
        this.jwt = jwt;
        this.passwordEncoder = passwordEncoder;
        this.currentCustomer = currentCustomer;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public record Session(ShopMeDto me, String accessToken, String refreshToken) {
    }

    @Transactional
    public Session register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String phone = PhoneNumbers.normalize(request.phone());
        if (accounts.existsByEmailIgnoreCase(email)) {
            throw ApiException.duplicate("email", "An account with this email already exists. Sign in instead.");
        }
        Customer customer;
        Optional<Customer> byPhone = customerRepository.findByPhone(phone);
        if (byPhone.isPresent()) {
            // The phone belongs to an in-store customer: joining needs proof of the physical card.
            Customer existing = byPhone.get();
            if (accounts.existsByCustomerId(existing.getId())) {
                throw ApiException.duplicate("phone", "This phone number already has an online account.");
            }
            LoyaltyAccount card = loyaltyAccounts.findByCustomerId(existing.getId()).orElse(null);
            String given = request.loyaltyCardNumber() == null ? "" : request.loyaltyCardNumber().trim().toUpperCase(Locale.ROOT);
            if (card == null || !card.getCardNumber().equals(given)) {
                throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "This phone number is registered to a loyalty card. "
                        + "Enter the card number to connect your in-store purchases.", java.util.Map.of("field", "loyaltyCardNumber"));
            }
            customer = existing;
            if (customer.getEmail() == null && !customerRepository.existsByEmailIgnoreCase(email)) {
                customer.setEmail(email);
            }
        } else {
            if (customerRepository.existsByEmailIgnoreCase(email)) {
                throw ApiException.duplicate("email", "This email is already registered to a customer.");
            }
            customer = customers.createInternal(request.fullName(), phone, email, request.marketingConsent(), true);
        }
        CustomerAccount account = new CustomerAccount();
        account.setCustomerId(customer.getId());
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setPreferredStoreId(request.preferredStoreId());
        account.setLastLoginAt(clock.instant());
        CustomerAccount saved = accounts.saveAndFlush(account);
        notifications.notify(saved.getId(), null, "ACCOUNT", "Welcome to the online shop",
                "Order online and collect in store. Your loyalty points work in both.");
        audit.record(new AuditService.Entry("SHOP_ACCOUNT_CREATED", "CustomerAccount", saved.getId(), AuditOutcome.SUCCESS,
                byPhone.isPresent() ? "Linked to existing loyalty customer" : "New customer", null, null, null, email));
        return start(saved, customer);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Session login(ShopLoginRequest request) {
        Instant now = clock.instant();
        Optional<CustomerAccount> found = accounts.findByEmailIgnoreCase(request.email().trim());
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw invalid();
        }
        CustomerAccount account = found.get();
        if (account.getLockedUntil() != null && account.getLockedUntil().isAfter(now)) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED, "Too many failed attempts. Try again in 15 minutes.");
        }
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            account.setFailedLoginCount(account.getFailedLoginCount() + 1);
            if (account.getFailedLoginCount() >= MAX_FAILURES) {
                account.setLockedUntil(now.plus(LOCKOUT));
                account.setFailedLoginCount(0);
            }
            throw invalid();
        }
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "This account is disabled.");
        }
        account.setFailedLoginCount(0);
        account.setLockedUntil(null);
        account.setLastLoginAt(now);
        return start(account, customers.require(account.getCustomerId()));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String raw) {
        if (raw == null || raw.isBlank()) {
            throw sessionEnded();
        }
        Instant now = clock.instant();
        CustomerRefreshToken current = tokens.findByHashForUpdate(SecureTokens.sha256(raw)).orElseThrow(this::sessionEnded);
        if (current.getReplacedById() != null) {
            boolean race = current.getRevokedAt() != null && Duration.between(current.getRevokedAt(), now).compareTo(ROTATION_GRACE) < 0;
            if (!race) {
                tokens.revokeFamily(current.getFamilyId(), now, "REUSE_DETECTED");
                audit.record(new AuditService.Entry("SHOP_TOKEN_REUSE", "CustomerAccount", current.getAccountId(),
                        AuditOutcome.DENIED, "Rotated shop refresh token reused; session revoked", null, null, null, null));
            }
            throw sessionEnded();
        }
        if (current.getRevokedAt() != null || !current.getExpiresAt().isAfter(now)) {
            throw sessionEnded();
        }
        CustomerAccount account = accounts.findById(current.getAccountId()).orElseThrow(this::sessionEnded);
        if (!"ACTIVE".equals(account.getStatus())) {
            tokens.revokeFamily(current.getFamilyId(), now, "DISABLED");
            throw sessionEnded();
        }
        String next = SecureTokens.newToken("srt_");
        CustomerRefreshToken saved = tokens.save(token(account.getId(), next, current.getFamilyId(), now, current.getFamilyExpiresAt()));
        current.setRevokedAt(now);
        current.setRevokeReason("ROTATED");
        current.setReplacedById(saved.getId());
        return session(account, customers.require(account.getCustomerId()), next);
    }

    @Transactional
    public void logout(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        tokens.findByTokenHash(SecureTokens.sha256(raw))
                .ifPresent(t -> tokens.revokeFamily(t.getFamilyId(), clock.instant(), "LOGOUT"));
    }

    @Transactional(readOnly = true)
    public ShopMeDto me() {
        CurrentCustomer c = currentCustomer.require();
        CustomerAccount account = accounts.findById(c.accountId()).orElseThrow(this::sessionEnded);
        return toMe(account, customers.require(account.getCustomerId()));
    }

    @Transactional
    public ShopMeDto setPreferredStore(Long storeId) {
        CurrentCustomer c = currentCustomer.require();
        stores.requireStore(storeId);
        CustomerAccount account = accounts.findById(c.accountId()).orElseThrow(this::sessionEnded);
        account.setPreferredStoreId(storeId);
        return toMe(account, customers.require(account.getCustomerId()));
    }

    /**
     * Links an in-store loyalty card to this account after sign-up. Both the printed card number and
     * the phone on file must match, so nobody can claim another shopper's purchase history.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public ShopMeDto linkCard(LinkCardRequest request) {
        CurrentCustomer c = currentCustomer.require();
        CustomerAccount account = accounts.findById(c.accountId()).orElseThrow(this::sessionEnded);
        LoyaltyAccount card = loyaltyAccounts.findByCardNumber(request.cardNumber().trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> cardMismatch());
        Customer cardOwner = customers.require(card.getCustomerId());
        String phone = PhoneNumbers.normalize(request.phone());
        if (cardOwner.getPhone() == null || !cardOwner.getPhone().equals(phone)) {
            audit.record(new AuditService.Entry("SHOP_CARD_LINK_FAILED", "CustomerAccount", account.getId(),
                    AuditOutcome.DENIED, "Card/phone mismatch", null, null, null, account.getEmail()));
            throw cardMismatch();
        }
        if (cardOwner.getId().equals(account.getCustomerId())) {
            return toMe(account, cardOwner);
        }
        if (accounts.existsByCustomerId(cardOwner.getId())) {
            throw ApiException.rule("This card is already linked to another online account.");
        }
        Customer onlineOnly = customers.require(account.getCustomerId());
        if (loyaltyAccounts.findByCustomerId(onlineOnly.getId()).map(LoyaltyAccount::getPointsBalance).orElse(0L) > 0) {
            throw ApiException.rule("Your online account already has points. Contact the store to merge the two cards.");
        }
        // The account now belongs to the in-store customer: their receipts and points follow.
        account.setCustomerId(cardOwner.getId());
        notifications.notify(account.getId(), null, "ACCOUNT", "Loyalty card connected",
                "In-store purchases on card " + card.getCardNumber() + " now appear in your account.");
        audit.record(new AuditService.Entry("SHOP_CARD_LINKED", "CustomerAccount", account.getId(), AuditOutcome.SUCCESS,
                "Linked card " + card.getCardNumber(), null, null, null, account.getEmail()));
        return toMe(account, cardOwner);
    }

    private Session start(CustomerAccount account, Customer customer) {
        Instant now = clock.instant();
        String raw = SecureTokens.newToken("srt_");
        tokens.save(token(account.getId(), raw, UUID.randomUUID().toString(), now,
                now.plus(properties.security().refreshFamilyMaxLifetime())));
        return session(account, customer, raw);
    }

    private Session session(CustomerAccount account, Customer customer, String refresh) {
        var access = jwt.issueCustomerToken(account.getId(), customer.getId(), customer.getFullName(), account.getEmail());
        return new Session(toMe(account, customer), access.value(), refresh);
    }

    private CustomerRefreshToken token(Long accountId, String raw, String family, Instant now, Instant familyExpiry) {
        CustomerRefreshToken t = new CustomerRefreshToken();
        t.setAccountId(accountId);
        t.setTokenHash(SecureTokens.sha256(raw));
        t.setFamilyId(family);
        t.setIssuedAt(now);
        t.setExpiresAt(familyExpiry);
        t.setFamilyExpiresAt(familyExpiry);
        return t;
    }

    private ShopMeDto toMe(CustomerAccount account, Customer customer) {
        LoyaltyAccount card = loyaltyAccounts.findByCustomerId(customer.getId()).orElse(null);
        long points = card == null ? 0 : card.getPointsBalance();
        Long storeId = account.getPreferredStoreId();
        BigDecimal value = LoyaltyService.valueOf(loyalty.rules(storeId), points);
        return new ShopMeDto(account.getId(), customer.getId(), customer.getFullName(), account.getEmail(),
                PhoneNumbers.mask(customer.getPhone()), card == null ? null : card.getCardNumber(), points, value,
                storeId, notifications.unread(account.getId()));
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, "Incorrect email or password.");
    }

    private ApiException sessionEnded() {
        return new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "Your session has ended. Please sign in again.");
    }

    private static ApiException cardMismatch() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "The card number and phone do not match our records.");
    }
}
