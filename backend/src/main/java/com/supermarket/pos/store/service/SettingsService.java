package com.supermarket.pos.store.service;

import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.store.dto.StoreDtos.SettingDto;
import com.supermarket.pos.store.dto.StoreDtos.SettingValue;
import com.supermarket.pos.store.entity.Setting;
import com.supermarket.pos.store.repository.SettingRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed configuration with store-level overrides of global defaults. Business modules read
 * values through the typed getters, which fall back to the supplied default if a key is missing.
 */
@Service
public class SettingsService {

    private final SettingRepository settings;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;
    private final Clock clock;

    public SettingsService(SettingRepository settings, CurrentUserProvider currentUser, AuditService audit,
                           Clock clock) {
        this.settings = settings;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BigDecimal decimal(Long storeId, String key, BigDecimal fallback) {
        String raw = raw(storeId, key);
        return raw == null ? fallback : new BigDecimal(raw);
    }

    @Transactional(readOnly = true)
    public int integer(Long storeId, String key, int fallback) {
        String raw = raw(storeId, key);
        return raw == null ? fallback : Integer.parseInt(raw);
    }

    @Transactional(readOnly = true)
    public boolean bool(Long storeId, String key, boolean fallback) {
        String raw = raw(storeId, key);
        return raw == null ? fallback : Boolean.parseBoolean(raw);
    }

    @Transactional(readOnly = true)
    public String string(Long storeId, String key, String fallback) {
        String raw = raw(storeId, key);
        return raw == null ? fallback : raw;
    }

    @Transactional(readOnly = true)
    public List<SettingDto> effective(Long storeId) {
        Map<String, Setting> merged = new HashMap<>();
        for (Setting s : settings.findEffective(storeId)) {
            merged.merge(s.getKey(), s, (a, b) -> a.getStoreId() != null ? a : b);
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(Setting::getKey))
                .map(s -> new SettingDto(s.getKey(), s.getValue(), s.getValueType().name(),
                        s.getStoreId() == null ? "GLOBAL" : "STORE"))
                .toList();
    }

    /** Writes store-level overrides (or global values when {@code storeId} is null). Keys must already exist. */
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    @Transactional
    public List<SettingDto> update(Long storeId, List<SettingValue> values) {
        Long userId = currentUser.require().id();
        for (SettingValue value : values) {
            Setting global = settings.findByStoreIdIsNullAndKey(value.key())
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown setting " + value.key()));
            validate(global.getValueType(), value);
            Setting target = storeId == null ? global
                    : settings.findByStoreIdAndKey(storeId, value.key()).orElseGet(() -> {
                        Setting s = new Setting();
                        s.setStoreId(storeId);
                        s.setKey(value.key());
                        s.setValueType(global.getValueType());
                        return s;
                    });
            String old = target.getValue();
            target.setValue(value.value().trim());
            target.setUpdatedBy(userId);
            target.setUpdatedAt(clock.instant());
            settings.save(target);
            audit.record(AuditService.Entry.success("SETTING_CHANGED", "Setting", value.key(),
                    value.key() + " changed").withStore(storeId)
                    .withDetails(Map.of("old", String.valueOf(old), "new", value.value().trim())));
        }
        return effective(storeId);
    }

    private String raw(Long storeId, String key) {
        if (storeId != null) {
            var override = settings.findByStoreIdAndKey(storeId, key);
            if (override.isPresent()) {
                return override.get().getValue();
            }
        }
        return settings.findByStoreIdIsNullAndKey(key).map(Setting::getValue).orElse(null);
    }

    private static void validate(Setting.ValueType type, SettingValue value) {
        String v = value.value().trim();
        try {
            switch (type) {
                case INTEGER -> Integer.parseInt(v);
                case DECIMAL -> {
                    if (new BigDecimal(v).signum() < 0) {
                        throw new NumberFormatException("negative");
                    }
                }
                case BOOLEAN -> {
                    if (!v.equals("true") && !v.equals("false")) {
                        throw new IllegalArgumentException("not a boolean");
                    }
                }
                case DURATION -> java.time.Duration.parse(v);
                case STRING -> {
                }
            }
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Invalid value for " + value.key() + " (expected " + type.name().toLowerCase() + ")");
        }
    }
}
