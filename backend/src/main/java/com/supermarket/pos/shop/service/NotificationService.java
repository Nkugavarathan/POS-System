package com.supermarket.pos.shop.service;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.web.PageRequests;
import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.customers.service.CustomerService;
import com.supermarket.pos.shop.dto.ShopDtos.NotificationDto;
import com.supermarket.pos.shop.entity.CustomerNotification;
import com.supermarket.pos.shop.entity.NotificationOutbox;
import com.supermarket.pos.shop.repository.CustomerAccountRepository;
import com.supermarket.pos.shop.repository.CustomerNotificationRepository;
import com.supermarket.pos.shop.repository.NotificationOutboxRepository;
import com.supermarket.pos.shop.repository.OnlineOrderRepository;
import com.supermarket.pos.shop.entity.OnlineOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Customer notifications. Each one is an in-app message (bell/inbox) plus outbox rows for email
 * and SMS, written in the same transaction as the change that caused it (transactional outbox);
 * {@link #dispatch()} delivers them asynchronously through {@link NotificationSender}.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final CustomerNotificationRepository notifications;
    private final NotificationOutboxRepository outbox;
    private final CustomerAccountRepository accounts;
    private final OnlineOrderRepository orders;
    private final CustomerService customers;
    private final ObjectProvider<NotificationSender> sender;
    private final Clock clock;

    public NotificationService(CustomerNotificationRepository notifications, NotificationOutboxRepository outbox,
                               CustomerAccountRepository accounts, OnlineOrderRepository orders,
                               CustomerService customers, ObjectProvider<NotificationSender> sender, Clock clock) {
        this.notifications = notifications;
        this.outbox = outbox;
        this.accounts = accounts;
        this.orders = orders;
        this.customers = customers;
        this.sender = sender;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notify(Long accountId, Long orderId, String kind, String title, String body) {
        CustomerNotification n = new CustomerNotification();
        n.setCustomerAccountId(accountId);
        n.setOnlineOrderId(orderId);
        n.setKind(kind);
        n.setTitle(truncate(title, 120));
        n.setBody(truncate(body, 500));
        n.setCreatedAt(clock.instant());
        CustomerNotification saved = notifications.save(n);
        accounts.findById(accountId).ifPresent(account -> {
            queue(saved, "EMAIL", account.getEmail());
            String phone = customers.require(account.getCustomerId()).getPhone();
            if (phone != null) {
                queue(saved, "SMS", phone);
            }
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(Long accountId, Integer page, Integer size) {
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "id"));
        var result = notifications.findByCustomerAccountIdOrderByIdDesc(accountId, pageable);
        // Order numbers for deep links, loaded in one query for the page.
        Map<Long, String> numbers = orders.findAllById(result.getContent().stream()
                        .map(CustomerNotification::getOnlineOrderId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(OnlineOrder::getId, OnlineOrder::getOrderNumber, (a, b) -> a));
        return PageResponse.of(result,
                n -> new NotificationDto(n.getId(), n.getKind(), n.getTitle(), n.getBody(), n.getOnlineOrderId(),
                        numbers.get(n.getOnlineOrderId()), n.getReadAt() != null, n.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public long unread(Long accountId) {
        return notifications.countByCustomerAccountIdAndReadAtIsNull(accountId);
    }

    @Transactional
    public void markRead(Long accountId, Long id) {
        CustomerNotification n = notifications.findById(id)
                .filter(x -> x.getCustomerAccountId().equals(accountId))
                .orElseThrow(() -> ApiException.notFound("Notification", id));
        if (n.getReadAt() == null) {
            n.setReadAt(clock.instant());
        }
    }

    @Transactional
    public void markAllRead(Long accountId) {
        notifications.markAllRead(accountId, clock.instant());
    }

    /** Delivers pending email/SMS; failures are retried (up to 5 attempts) on later runs. */
    @Scheduled(fixedDelay = 15_000, initialDelay = 20_000)
    @Transactional
    public void dispatch() {
        NotificationSender delivery = sender.getIfAvailable(LoggingNotificationSender::new);
        for (NotificationOutbox row : outbox.findPending(PageRequest.of(0, 50))) {
            row.setAttempts(row.getAttempts() + 1);
            try {
                delivery.send(row.getChannel(), row.getRecipient(), row.getSubject(), row.getBody());
                row.setStatus("SENT");
                row.setSentAt(clock.instant());
                row.setLastError(null);
            } catch (Exception e) {
                row.setLastError(truncate(e.getMessage(), 255));
                if (row.getAttempts() >= 5) {
                    row.setStatus("FAILED");
                    log.warn("Giving up on {} notification {} after 5 attempts", row.getChannel(), row.getId());
                }
            }
        }
    }

    private void queue(CustomerNotification n, String channel, String recipient) {
        NotificationOutbox row = new NotificationOutbox();
        row.setNotificationId(n.getId());
        row.setChannel(channel);
        row.setRecipient(recipient);
        row.setSubject(n.getTitle());
        row.setBody(n.getBody());
        row.setCreatedAt(clock.instant());
        outbox.save(row);
    }

    private static String truncate(String v, int max) {
        return v == null ? null : v.length() <= max ? v : v.substring(0, max);
    }

    /** Development sender: logs instead of sending (no email/SMS provider is configured). */
    public static class LoggingNotificationSender implements NotificationSender {
        @Override
        public void send(String channel, String recipient, String subject, String body) {
            String masked = recipient.length() > 4 ? "***" + recipient.substring(recipient.length() - 4) : "***";
            log.info("[notification:{}] to {} | {} | {}", channel, masked, subject, body);
        }
    }
}
