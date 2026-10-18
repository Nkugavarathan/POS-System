package com.supermarket.pos.products.service;

import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.products.dto.ProductDtos.TaxRateDto;
import com.supermarket.pos.products.dto.ProductDtos.TaxRateRequest;
import com.supermarket.pos.products.entity.TaxRate;
import com.supermarket.pos.products.mapper.ProductMapper;
import com.supermarket.pos.products.repository.TaxRateRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Tax rates are configuration owned by SYSTEM_CONFIG. Changing a rate affects future sales
 * only; completed sales keep the percentage they were sold at.
 */
@Service
public class TaxRateService {

    private final TaxRateRepository taxRates;
    private final AuditService audit;
    private final Clock clock;

    public TaxRateService(TaxRateRepository taxRates, AuditService audit, Clock clock) {
        this.taxRates = taxRates;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TaxRateDto> list() {
        return taxRates.findAllByOrderByCodeAsc().stream().map(ProductMapper::toTaxRate).toList();
    }

    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    @Transactional
    public TaxRateDto create(TaxRateRequest request) {
        if (taxRates.existsByCodeIgnoreCase(request.code())) {
            throw ApiException.duplicate("code", "Tax rate " + request.code() + " already exists.");
        }
        TaxRate rate = new TaxRate();
        rate.setCode(request.code());
        rate.setEffectiveFrom(LocalDate.now(clock));
        apply(rate, request);
        TaxRate saved = taxRates.saveAndFlush(rate);
        audit.record(AuditService.Entry.success("TAX_RATE_CREATED", "TaxRate", saved.getId(),
                saved.getCode() + " " + saved.getRatePercent().toPlainString() + "%"));
        return ProductMapper.toTaxRate(saved);
    }

    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    @Transactional
    public TaxRateDto update(Long id, TaxRateRequest request) {
        TaxRate rate = taxRates.findById(id).orElseThrow(() -> ApiException.notFound("Tax rate", id));
        String old = rate.getRatePercent().toPlainString();
        apply(rate, request);
        if (rate.getRatePercent().compareTo(new java.math.BigDecimal(old)) != 0) {
            rate.setEffectiveFrom(LocalDate.now(clock));
        }
        TaxRate saved = taxRates.saveAndFlush(rate);
        audit.record(AuditService.Entry.success("TAX_RATE_CHANGED", "TaxRate", id, rate.getCode())
                .withDetails(Map.of("old", old, "new", saved.getRatePercent().toPlainString())));
        return ProductMapper.toTaxRate(saved);
    }

    private static void apply(TaxRate rate, TaxRateRequest request) {
        rate.setName(request.name().trim());
        rate.setRatePercent(request.ratePercent().setScale(3, java.math.RoundingMode.HALF_UP));
        rate.setActive(request.active());
    }
}
