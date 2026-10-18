package com.supermarket.pos.store.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class StoreDtos {

    private StoreDtos() {
    }

    public record StoreSummaryDto(Long id, String code, String name) {
    }

    public record StoreDto(Long id, String code, String name, String addressLine1, String addressLine2, String city,
                           String phone, String email, String taxRegistrationNo, String currency, String timezone,
                           boolean pricesIncludeTax, String receiptHeader, String receiptFooter, boolean active,
                           long version) {
    }

    public record UpdateStoreRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 150) String addressLine1,
            @Size(max = 150) String addressLine2,
            @Size(max = 80) String city,
            @Size(max = 30) String phone,
            @Email @Size(max = 120) String email,
            @Size(max = 40) String taxRegistrationNo,
            @NotNull Boolean pricesIncludeTax,
            @Size(max = 500) String receiptHeader,
            @Size(max = 500) String receiptFooter,
            @NotNull Long version) {
    }

    public record RegisterDto(Long id, Long storeId, String code, String name, boolean active, long version) {
    }

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9-]{1,20}$", message = "1-20 upper-case letters, digits or dashes")
            String code,
            @NotBlank @Size(max = 80) String name,
            @NotNull Boolean active,
            Long version) {
    }

    public record TenderTypeDto(String code, String name, boolean cash, boolean requiresReference,
                                boolean allowsChange, boolean active, int sortOrder) {
    }

    public record SettingDto(String key, String value, String valueType, String scope) {
    }

    public record UpdateSettingsRequest(@NotNull List<@NotNull SettingValue> settings) {
    }

    public record SettingValue(@NotBlank String key, @NotNull @Size(max = 500) String value) {
    }
}
