package com.supermarket.pos.store.controller;

import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.store.dto.StoreDtos.RegisterDto;
import com.supermarket.pos.store.dto.StoreDtos.RegisterRequest;
import com.supermarket.pos.store.dto.StoreDtos.SettingDto;
import com.supermarket.pos.store.dto.StoreDtos.StoreDto;
import com.supermarket.pos.store.dto.StoreDtos.StoreSummaryDto;
import com.supermarket.pos.store.dto.StoreDtos.TenderTypeDto;
import com.supermarket.pos.store.dto.StoreDtos.UpdateSettingsRequest;
import com.supermarket.pos.store.dto.StoreDtos.UpdateStoreRequest;
import com.supermarket.pos.store.service.SettingsService;
import com.supermarket.pos.store.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Stores", description = "Stores, registers, tender types and settings")
@RestController
@RequestMapping("/api/v1")
public class StoreController {

    private final StoreService stores;
    private final SettingsService settings;
    private final CurrentUserProvider currentUser;

    public StoreController(StoreService stores, SettingsService settings, CurrentUserProvider currentUser) {
        this.stores = stores;
        this.settings = settings;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Stores the current user can access")
    @GetMapping("/stores")
    public List<StoreSummaryDto> accessibleStores() {
        return stores.accessibleStores();
    }

    @GetMapping("/stores/{id}")
    public StoreDto get(@PathVariable Long id) {
        return stores.get(id);
    }

    @PutMapping("/stores/{id}")
    public StoreDto update(@PathVariable Long id, @Valid @RequestBody UpdateStoreRequest request) {
        return stores.update(id, request);
    }

    @GetMapping("/stores/{storeId}/registers")
    public List<RegisterDto> registers(@PathVariable Long storeId) {
        return stores.registers(storeId);
    }

    @PostMapping("/stores/{storeId}/registers")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterDto createRegister(@PathVariable Long storeId, @Valid @RequestBody RegisterRequest request) {
        return stores.createRegister(storeId, request);
    }

    @PutMapping("/stores/{storeId}/registers/{registerId}")
    public RegisterDto updateRegister(@PathVariable Long storeId, @PathVariable Long registerId,
                                      @Valid @RequestBody RegisterRequest request) {
        return stores.updateRegister(storeId, registerId, request);
    }

    @GetMapping("/tender-types")
    public List<TenderTypeDto> tenderTypes() {
        return stores.tenderTypes();
    }

    @Operation(summary = "Effective settings for the active store (store overrides merged over global defaults)")
    @PreAuthorize("hasAnyAuthority('SYSTEM_CONFIG','REPORT_VIEW','POS_SELL')")
    @GetMapping("/settings")
    public List<SettingDto> settings() {
        return settings.effective(currentUser.require().requireActiveStore());
    }

    @Operation(summary = "Update settings; scope=GLOBAL changes defaults, otherwise the active store is overridden")
    @PutMapping("/settings")
    public List<SettingDto> updateSettings(@RequestParam(defaultValue = "STORE") String scope,
                                           @Valid @RequestBody UpdateSettingsRequest request) {
        Long storeId = "GLOBAL".equalsIgnoreCase(scope) ? null : currentUser.require().requireActiveStore();
        return settings.update(storeId, request.settings());
    }
}
