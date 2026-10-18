package com.supermarket.pos.customers.service;

import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.security.CurrentUser;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.common.sequence.SequenceService;
import com.supermarket.pos.common.web.PageRequests;
import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.customers.dto.CustomerDtos.CustomerDto;
import com.supermarket.pos.customers.dto.CustomerDtos.CustomerRequest;
import com.supermarket.pos.customers.dto.CustomerDtos.CustomerSummaryDto;
import com.supermarket.pos.customers.dto.CustomerDtos.LoyaltyDto;
import com.supermarket.pos.customers.dto.CustomerDtos.LoyaltyTransactionDto;
import com.supermarket.pos.customers.dto.CustomerDtos.PosCustomerDto;
import com.supermarket.pos.customers.entity.Customer;
import com.supermarket.pos.customers.entity.LoyaltyAccount;
import com.supermarket.pos.customers.entity.LoyaltyTransaction;
import com.supermarket.pos.customers.repository.CustomerRepository;
import com.supermarket.pos.customers.repository.LoyaltyAccountRepository;
import com.supermarket.pos.customers.repository.LoyaltyTransactionRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    private static final Map<String, String> SORTS = Map.of("fullName", "fullName", "createdAt", "createdAt");

    private final CustomerRepository customers;
    private final LoyaltyAccountRepository loyaltyAccounts;
    private final LoyaltyTransactionRepository loyaltyTransactions;
    private final LoyaltyService loyalty;
    private final SequenceService sequences;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, LoyaltyAccountRepository loyaltyAccounts,
                           LoyaltyTransactionRepository loyaltyTransactions, LoyaltyService loyalty,
                           SequenceService sequences, CurrentUserProvider currentUser, AuditService audit, Clock clock) {
        this.customers = customers;
        this.loyaltyAccounts = loyaltyAccounts;
        this.loyaltyTransactions = loyaltyTransactions;
        this.loyalty = loyalty;
        this.sequences = sequences;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    @Transactional(readOnly = true)
    public PageResponse<CustomerSummaryDto> search(String q, Boolean active, Integer page, Integer size, List<String> sort) {
        var pageable = PageRequests.of(page, size, sort, SORTS, Sort.by("fullName"));
        String query = q == null || q.isBlank() ? null : q.trim();
        var result = customers.search(query, active, pageable);
        Map<Long, LoyaltyAccount> cards = loyaltyAccounts.findByCustomerIdIn(
                        result.getContent().stream().map(Customer::getId).toList()).stream()
                .collect(Collectors.toMap(LoyaltyAccount::getCustomerId, Function.identity()));
        return PageResponse.of(result, c -> {
            LoyaltyAccount a = cards.get(c.getId());
            return new CustomerSummaryDto(c.getId(), c.getCustomerCode(), c.getFullName(), PhoneNumbers.mask(c.getPhone()),
                    c.getEmail(), a == null ? null : a.getCardNumber(), a == null ? 0 : a.getPointsBalance(), c.isActive());
        });
    }

    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    @Transactional(readOnly = true)
    public CustomerDto get(Long id) {
        return toDto(require(id));
    }

    /** Cashier lookup by loyalty card number or phone. 404 if none. */
    @PreAuthorize("hasAnyAuthority('POS_SELL','CUSTOMER_VIEW')")
    @Transactional(readOnly = true)
    public PosCustomerDto lookup(String cardOrPhone) {
        Long storeId = currentUser.require().requireActiveStore();
        String value = cardOrPhone == null ? "" : cardOrPhone.trim();
        Customer customer = loyaltyAccounts.findByCardNumber(value.toUpperCase(Locale.ROOT))
                .flatMap(a -> customers.findById(a.getCustomerId()))
                .or(() -> {
                    try {
                        return customers.findByPhone(PhoneNumbers.normalize(value));
                    } catch (ApiException notAPhone) {
                        return java.util.Optional.empty();
                    }
                })
                .filter(Customer::isActive)
                .orElseThrow(() -> ApiException.notFound("Customer", value));
        return toPos(customer, storeId);
    }

    @Transactional(readOnly = true)
    public PosCustomerDto posView(Long customerId, Long storeId) {
        return toPos(require(customerId), storeId);
    }

    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    @Transactional
    public CustomerDto create(CustomerRequest request) {
        Customer customer = createInternal(request.fullName(), request.phone(), request.email(), request.marketingConsent(),
                request.enrolLoyalty());
        audit.record(AuditService.Entry.success("CUSTOMER_CREATED", "Customer", customer.getId(), customer.getCustomerCode()));
        return toDto(customer);
    }

    /** Used by staff screens and by online sign-up. Phone and email must be unique. */
    @Transactional
    public Customer createInternal(String fullName, String rawPhone, String rawEmail, boolean consent, boolean enrol) {
        String phone = PhoneNumbers.normalize(rawPhone);
        String email = rawEmail == null || rawEmail.isBlank() ? null : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (phone != null && customers.existsByPhone(phone)) {
            throw ApiException.duplicate("phone", "A customer with this phone number already exists.");
        }
        if (email != null && customers.existsByEmailIgnoreCase(email)) {
            throw ApiException.duplicate("email", "A customer with this email already exists.");
        }
        Customer c = new Customer();
        c.setFullName(fullName.trim());
        c.setPhone(phone);
        c.setEmail(email);
        c.setMarketingConsent(consent);
        c.setCustomerCode("C" + String.format("%07d", sequences.next("CUSTOMER", "GLOBAL", "ALL")));
        Customer saved = customers.saveAndFlush(c);
        if (enrol) {
            enrolLoyalty(saved);
        }
        return saved;
    }

    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    @Transactional
    public CustomerDto update(Long id, CustomerRequest request) {
        Customer c = require(id);
        if (request.version() == null || c.getVersion() != request.version()) {
            throw new OptimisticLockingFailureException("Customer changed");
        }
        String phone = PhoneNumbers.normalize(request.phone());
        String email = request.email() == null || request.email().isBlank() ? null : request.email().trim().toLowerCase(Locale.ROOT);
        if (phone != null && !phone.equals(c.getPhone()) && customers.existsByPhone(phone)) {
            throw ApiException.duplicate("phone", "A customer with this phone number already exists.");
        }
        if (email != null && !email.equalsIgnoreCase(c.getEmail()) && customers.existsByEmailIgnoreCase(email)) {
            throw ApiException.duplicate("email", "A customer with this email already exists.");
        }
        c.setFullName(request.fullName().trim());
        c.setPhone(phone);
        c.setEmail(email);
        c.setMarketingConsent(request.marketingConsent());
        if (request.enrolLoyalty() && loyaltyAccounts.findByCustomerId(id).isEmpty()) {
            enrolLoyalty(c);
        }
        customers.saveAndFlush(c);
        audit.record(AuditService.Entry.success("CUSTOMER_UPDATED", "Customer", id, c.getCustomerCode()));
        return toDto(c);
    }

    @PreAuthorize("hasAuthority('LOYALTY_ADJUST')")
    @Transactional
    public CustomerDto adjustPoints(Long customerId, long points, String reason) {
        CurrentUser user = currentUser.require();
        require(customerId);
        loyalty.post(customerId, LoyaltyTransaction.Type.ADJUSTMENT, points, "MANUAL", null, reason.trim(), user.id());
        audit.record(AuditService.Entry.success("LOYALTY_ADJUSTED", "Customer", customerId, points + " points: " + reason));
        return toDto(require(customerId));
    }

    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    @Transactional(readOnly = true)
    public PageResponse<LoyaltyTransactionDto> loyaltyHistory(Long customerId, Integer page, Integer size) {
        LoyaltyAccount account = loyaltyAccounts.findByCustomerId(customerId)
                .orElseThrow(() -> ApiException.notFound("Loyalty account", customerId));
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.of(loyaltyTransactions.findByLoyaltyAccountId(account.getId(), pageable),
                t -> new LoyaltyTransactionDto(t.getId(), t.getTransactionType(), t.getPoints(), t.getBalanceAfter(),
                        t.getReferenceType(), t.getReferenceId(), t.getReason(), t.getCreatedAt()));
    }

    public Customer require(Long id) {
        return customers.findById(id).orElseThrow(() -> ApiException.notFound("Customer", id));
    }

    private void enrolLoyalty(Customer customer) {
        LoyaltyAccount a = new LoyaltyAccount();
        a.setCustomerId(customer.getId());
        a.setCardNumber("LC" + String.format("%08d", sequences.next("LOYALTY_CARD", "GLOBAL", "ALL")));
        a.setJoinedAt(clock.instant());
        loyaltyAccounts.saveAndFlush(a);
    }

    private PosCustomerDto toPos(Customer c, Long storeId) {
        LoyaltyAccount a = loyaltyAccounts.findByCustomerId(c.getId()).orElse(null);
        var rules = loyalty.rules(storeId);
        long points = a == null ? 0 : a.getPointsBalance();
        return new PosCustomerDto(c.getId(), c.getFullName(), PhoneNumbers.mask(c.getPhone()),
                a == null ? null : a.getCardNumber(), points, LoyaltyService.valueOf(rules, points));
    }

    private CustomerDto toDto(Customer c) {
        LoyaltyAccount a = loyaltyAccounts.findByCustomerId(c.getId()).orElse(null);
        Long storeId = currentUser.current().map(CurrentUser::activeStoreId).orElse(null);
        LoyaltyDto loyaltyDto = a == null ? null : new LoyaltyDto(a.getCardNumber(), a.getPointsBalance(),
                LoyaltyService.valueOf(loyalty.rules(storeId), a.getPointsBalance()), a.getStatus().name());
        return new CustomerDto(c.getId(), c.getCustomerCode(), c.getFullName(), c.getPhone(), c.getEmail(),
                c.isMarketingConsent(), c.isActive(), loyaltyDto, false, c.getCreatedAt(), c.getVersion());
    }
}
