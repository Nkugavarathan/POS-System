package com.supermarket.pos.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.pos.common.config.AppProperties;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Exactly-once execution of critical writes (checkout, returns, receipts, ...).
 *
 * <p>How it works: the business transaction itself inserts the idempotency row
 * ({@link #register}) as its first statement. A concurrent duplicate with the same key
 * blocks on the unique index until the first transaction finishes:
 * <ul>
 *   <li>first commits → the duplicate fails with a duplicate-key error → {@link #execute} replays the stored result;</li>
 *   <li>first rolls back → the duplicate's insert succeeds and it runs normally.</li>
 * </ul>
 * There is therefore never an "in progress" row visible to other transactions.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Pattern KEY_FORMAT = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    private static final String UNIQUE_CONSTRAINT = "uk_idem";

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;
    private final Clock clock;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper,
                              AppProperties properties, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /** Identifies one idempotent request. */
    public record Request(Long userId, String operation, String key, String requestHash) {
    }

    /** The result plus whether it was replayed from an earlier, identical request. */
    public record Outcome<R>(R value, boolean replayed) {
    }

    public Request request(Long userId, String operation, String key, Object body) {
        if (key == null || key.isBlank()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "The Idempotency-Key header is required.");
        }
        if (!KEY_FORMAT.matcher(key).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key must be 8-64 letters, digits or dashes.");
        }
        return new Request(userId, operation, key, hash(body));
    }

    /**
     * Runs {@code action} (a {@code @Transactional} service call that starts with
     * {@link #register}) at most once per key, and replays the earlier result for repeats.
     *
     * @param replay loads the previously created resource by id
     */
    public <R> Outcome<R> execute(Request request, Supplier<R> action, Function<Long, R> replay) {
        Optional<IdempotencyRecord> existing = find(request);
        if (existing.isPresent()) {
            return new Outcome<>(replayExisting(request, existing.get(), replay), true);
        }
        try {
            return new Outcome<>(action.get(), false);
        } catch (DataIntegrityViolationException ex) {
            if (!isIdempotencyConflict(ex)) {
                throw ex;
            }
            log.info("Concurrent duplicate for idempotency key {} ({}), replaying", request.key(), request.operation());
            IdempotencyRecord record = find(request).orElseThrow(() -> new ApiException(
                    ErrorCode.IDEMPOTENCY_IN_PROGRESS, "The same request is still being processed. Retry shortly."));
            return new Outcome<>(replayExisting(request, record, replay), true);
        }
    }

    /** Inserts the idempotency row inside the business transaction. Call it first. */
    @Transactional(propagation = Propagation.MANDATORY)
    public IdempotencyRecord register(Request request) {
        Instant now = clock.instant();
        IdempotencyRecord record = new IdempotencyRecord();
        record.setUserId(request.userId());
        record.setOperation(request.operation());
        record.setIdempotencyKey(request.key());
        record.setRequestHash(request.requestHash());
        record.setCreatedAt(now);
        record.setExpiresAt(now.plus(properties.idempotency().retention()));
        return repository.saveAndFlush(record);
    }

    /** Records which resource the request produced. Same transaction as {@link #register}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(IdempotencyRecord record, String resourceType, Long resourceId, int status) {
        record.setResourceType(resourceType);
        record.setResourceId(resourceId);
        record.setResponseStatus(status);
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> find(Request request) {
        return repository.findByUserIdAndOperationAndIdempotencyKey(request.userId(), request.operation(), request.key());
    }

    public String hash(Object body) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(body);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot hash request body", e);
        }
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteExpired(clock.instant());
        if (removed > 0) {
            log.info("Purged {} expired idempotency records", removed);
        }
    }

    private <R> R replayExisting(Request request, IdempotencyRecord record, Function<Long, R> replay) {
        if (!record.getRequestHash().equals(request.requestHash())) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This Idempotency-Key was already used for a different request.");
        }
        if (record.getResourceId() == null) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_IN_PROGRESS,
                    "The same request is still being processed. Retry shortly.");
        }
        return replay.apply(record.getResourceId());
    }

    private static boolean isIdempotencyConflict(DataIntegrityViolationException ex) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        return message.contains(UNIQUE_CONSTRAINT);
    }
}
