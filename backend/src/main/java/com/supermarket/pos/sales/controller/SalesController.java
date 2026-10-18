package com.supermarket.pos.sales.controller;

import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.sales.dto.SalesDtos.CartRequest;
import com.supermarket.pos.sales.dto.SalesDtos.CheckoutRequest;
import com.supermarket.pos.sales.dto.SalesDtos.QuoteResponse;
import com.supermarket.pos.sales.dto.SalesDtos.SaleDto;
import com.supermarket.pos.sales.dto.SalesDtos.SaleSummaryDto;
import com.supermarket.pos.sales.entity.Sale;
import com.supermarket.pos.sales.service.CheckoutService;
import com.supermarket.pos.sales.service.SaleQueryService;
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
import java.util.List;

@Tag(name = "Sales", description = "Quote, checkout, sales history and receipts")
@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {

    private final CheckoutService checkout;
    private final SaleQueryService queries;

    public SalesController(CheckoutService checkout, SaleQueryService queries) {
        this.checkout = checkout;
        this.queries = queries;
    }

    @Operation(summary = "Price a basket (advisory; no stock is reserved)")
    @PostMapping("/quote")
    public QuoteResponse quote(@Valid @RequestBody CartRequest request) {
        return checkout.quote(request);
    }

    @Operation(summary = "Complete a sale. Requires Idempotency-Key; retries with the same key replay the sale.")
    @PostMapping("/checkout")
    public ResponseEntity<SaleDto> checkout(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                            @Valid @RequestBody CheckoutRequest request) {
        var outcome = checkout.checkout(request, key);
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(outcome.replayed()))
                .body(outcome.value());
    }

    @GetMapping
    public PageResponse<SaleSummaryDto> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                             @RequestParam(required = false) Long cashierId,
                                             @RequestParam(required = false) Long registerId,
                                             @RequestParam(required = false) Long customerId,
                                             @RequestParam(required = false) Sale.Status status,
                                             @RequestParam(required = false) Sale.Channel channel,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size,
                                             @RequestParam(required = false) List<String> sort) {
        return queries.list(from, to, cashierId, registerId, customerId, status, channel, q, page, size, sort);
    }

    @GetMapping("/{id}")
    public SaleDto get(@PathVariable Long id) {
        return queries.get(id);
    }

    @GetMapping("/receipt/{receiptNumber}")
    public SaleDto receipt(@PathVariable String receiptNumber) {
        return queries.byReceipt(receiptNumber);
    }

    @Operation(summary = "Reprint a receipt (marked COPY; no sale or stock change)")
    @PostMapping("/{id}/reprints")
    public SaleDto reprint(@PathVariable Long id) {
        return queries.reprint(id);
    }
}
