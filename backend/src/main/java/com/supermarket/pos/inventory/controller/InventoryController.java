package com.supermarket.pos.inventory.controller;

import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.inventory.dto.InventoryDtos.AdjustmentDto;
import com.supermarket.pos.inventory.dto.InventoryDtos.AdjustmentRequest;
import com.supermarket.pos.inventory.dto.InventoryDtos.AlertDto;
import com.supermarket.pos.inventory.dto.InventoryDtos.CountDto;
import com.supermarket.pos.inventory.dto.InventoryDtos.CreateCountRequest;
import com.supermarket.pos.inventory.dto.InventoryDtos.ExpiringBatchDto;
import com.supermarket.pos.inventory.dto.InventoryDtos.InventoryDetailDto;
import com.supermarket.pos.inventory.dto.InventoryDtos.InventoryRowDto;
import com.supermarket.pos.inventory.dto.InventoryDtos.MovementDto;
import com.supermarket.pos.inventory.dto.InventoryDtos.RecordCountRequest;
import com.supermarket.pos.inventory.entity.MovementType;
import com.supermarket.pos.inventory.service.InventoryQueryService;
import com.supermarket.pos.inventory.service.StockAdjustmentService;
import com.supermarket.pos.inventory.service.StockAlertService;
import com.supermarket.pos.inventory.service.StockCountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Tag(name = "Inventory", description = "Stock ledger, adjustments, counts and alerts")
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryQueryService queries;
    private final StockAdjustmentService adjustments;
    private final StockCountService counts;
    private final StockAlertService alerts;

    public InventoryController(InventoryQueryService queries, StockAdjustmentService adjustments,
                               StockCountService counts, StockAlertService alerts) {
        this.queries = queries;
        this.adjustments = adjustments;
        this.counts = counts;
        this.alerts = alerts;
    }

    @GetMapping
    public PageResponse<InventoryRowDto> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) Long categoryId,
                                              @RequestParam(defaultValue = "false") boolean lowStock,
                                              @RequestParam(defaultValue = "false") boolean inStockOnly,
                                              @RequestParam(defaultValue = "false") boolean includeInactive,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size,
                                              @RequestParam(required = false) List<String> sort) {
        return queries.list(q, categoryId, lowStock, inStockOnly, includeInactive, page, size, sort);
    }

    @GetMapping("/{productId}")
    public InventoryDetailDto detail(@PathVariable Long productId) {
        return queries.detail(productId);
    }

    @Operation(summary = "Stock movement history (date range at most 92 days)")
    @GetMapping("/movements")
    public PageResponse<MovementDto> movements(@RequestParam(required = false) Long productId,
                                               @RequestParam(required = false) MovementType type,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return queries.movements(productId, type, from, to, page, size);
    }

    @GetMapping("/expiring")
    public List<ExpiringBatchDto> expiring(@RequestParam(defaultValue = "30") int days) {
        return queries.expiring(days);
    }

    @Operation(summary = "Post an adjustment / write-off / opening balance (requires Idempotency-Key)")
    @PostMapping("/adjustments")
    public ResponseEntity<AdjustmentDto> adjust(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                @Valid @RequestBody AdjustmentRequest request) {
        var outcome = adjustments.create(request, key);
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(outcome.replayed()))
                .body(outcome.value());
    }

    @GetMapping("/adjustments")
    public PageResponse<AdjustmentDto> adjustments(@RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size) {
        return adjustments.list(page, size);
    }

    @GetMapping("/adjustments/{id}")
    public AdjustmentDto adjustment(@PathVariable Long id) {
        return adjustments.get(id);
    }

    @PostMapping("/counts")
    @ResponseStatus(HttpStatus.CREATED)
    public CountDto createCount(@Valid @RequestBody CreateCountRequest request) {
        return counts.create(request);
    }

    @GetMapping("/counts")
    public PageResponse<CountDto> countList(@RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        return counts.list(page, size);
    }

    @GetMapping("/counts/{id}")
    public CountDto count(@PathVariable Long id) {
        return counts.get(id);
    }

    @PutMapping("/counts/{id}/items")
    public CountDto recordCount(@PathVariable Long id, @Valid @RequestBody RecordCountRequest request) {
        return counts.record(id, request.entries(), request.version());
    }

    @PostMapping("/counts/{id}/submit")
    public CountDto submit(@PathVariable Long id) {
        return counts.submit(id);
    }

    @PostMapping("/counts/{id}/reopen")
    public CountDto reopen(@PathVariable Long id) {
        return counts.reopen(id);
    }

    @PostMapping("/counts/{id}/post")
    public CountDto post(@PathVariable Long id) {
        return counts.post(id);
    }

    @PostMapping("/counts/{id}/cancel")
    public CountDto cancel(@PathVariable Long id) {
        return counts.cancel(id);
    }

    @GetMapping("/alerts")
    public PageResponse<AlertDto> alerts(@RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        return alerts.open(page, size);
    }

    @PostMapping("/alerts/{id}/acknowledge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acknowledge(@PathVariable Long id) {
        alerts.acknowledge(id);
    }
}
