package com.supermarket.pos.returns.controller;

import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.returns.dto.ReturnDtos.PreviewDto;
import com.supermarket.pos.returns.dto.ReturnDtos.PreviewRequest;
import com.supermarket.pos.returns.dto.ReturnDtos.ReturnDto;
import com.supermarket.pos.returns.dto.ReturnDtos.ReturnRequest;
import com.supermarket.pos.returns.service.ReturnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "Returns", description = "Returns and refunds against completed sales")
@RestController
@RequestMapping("/api/v1/returns")
public class ReturnController {

    private final ReturnService returns;

    public ReturnController(ReturnService returns) {
        this.returns = returns;
    }

    @Operation(summary = "Compute the refund for the selected items (no changes are made)")
    @PostMapping("/preview")
    public PreviewDto preview(@Valid @RequestBody PreviewRequest request) {
        return returns.preview(request);
    }

    @Operation(summary = "Process a return (requires Idempotency-Key)")
    @PostMapping
    public ResponseEntity<ReturnDto> create(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                            @Valid @RequestBody ReturnRequest request) {
        var outcome = returns.create(request, key);
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(outcome.value());
    }

    @GetMapping
    public PageResponse<ReturnDto> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                        @RequestParam(required = false) Long saleId,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size) {
        return returns.list(from, to, saleId, page, size);
    }

    @GetMapping("/{id}")
    public ReturnDto get(@PathVariable Long id) {
        return returns.get(id);
    }
}
