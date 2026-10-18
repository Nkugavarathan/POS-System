package com.supermarket.pos.purchases.controller;

import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.purchases.dto.PurchaseDtos.GrnDto;
import com.supermarket.pos.purchases.dto.PurchaseDtos.GrnSummaryDto;
import com.supermarket.pos.purchases.dto.PurchaseDtos.PoDto;
import com.supermarket.pos.purchases.dto.PurchaseDtos.PoRequest;
import com.supermarket.pos.purchases.dto.PurchaseDtos.ReceiveRequest;
import com.supermarket.pos.purchases.dto.PurchaseDtos.SupplierDto;
import com.supermarket.pos.purchases.dto.PurchaseDtos.SupplierRequest;
import com.supermarket.pos.purchases.entity.PurchaseOrder;
import com.supermarket.pos.purchases.service.PurchaseService;
import com.supermarket.pos.suppliers.service.SupplierService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;

@Tag(name = "Purchasing", description = "Suppliers, purchase orders and goods receipts")
@RestController
@RequestMapping("/api/v1")
public class PurchaseController {

    private final SupplierService suppliers;
    private final PurchaseService purchases;

    public PurchaseController(SupplierService suppliers, PurchaseService purchases) {
        this.suppliers = suppliers;
        this.purchases = purchases;
    }

    @GetMapping("/suppliers")
    public PageResponse<SupplierDto> suppliers(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) Boolean active,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @RequestParam(required = false) List<String> sort) {
        return suppliers.search(q, active, page, size, sort);
    }

    @GetMapping("/suppliers/{id}")
    public SupplierDto supplier(@PathVariable Long id) {
        return suppliers.get(id);
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierDto createSupplier(@Valid @RequestBody SupplierRequest request) {
        return suppliers.create(request);
    }

    @PutMapping("/suppliers/{id}")
    public SupplierDto updateSupplier(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return suppliers.update(id, request);
    }

    @GetMapping("/purchases/orders")
    public PageResponse<PoDto> orders(@RequestParam(required = false) Long supplierId,
                                      @RequestParam(required = false) PurchaseOrder.Status status,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size) {
        return purchases.list(supplierId, status, q, page, size);
    }

    @GetMapping("/purchases/orders/{id}")
    public PoDto order(@PathVariable Long id) {
        return purchases.get(id);
    }

    @PostMapping("/purchases/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public PoDto createOrder(@Valid @RequestBody PoRequest request) {
        return purchases.create(request);
    }

    @PutMapping("/purchases/orders/{id}")
    public PoDto updateOrder(@PathVariable Long id, @Valid @RequestBody PoRequest request) {
        return purchases.update(id, request);
    }

    @PostMapping("/purchases/orders/{id}/submit")
    public PoDto submit(@PathVariable Long id) {
        return purchases.submit(id);
    }

    @PostMapping("/purchases/orders/{id}/close")
    public PoDto close(@PathVariable Long id) {
        return purchases.close(id);
    }

    @PostMapping("/purchases/orders/{id}/cancel")
    public PoDto cancel(@PathVariable Long id) {
        return purchases.cancel(id);
    }

    @PostMapping("/purchases/orders/{id}/receipts")
    public ResponseEntity<GrnDto> receive(@PathVariable Long id,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                          @Valid @RequestBody ReceiveRequest request) {
        var outcome = purchases.receive(id, request, key);
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(outcome.value());
    }

    @PostMapping("/purchases/receipts")
    public ResponseEntity<GrnDto> receiveWithoutOrder(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                      @Valid @RequestBody ReceiveRequest request) {
        var outcome = purchases.receive(null, request, key);
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(outcome.value());
    }

    @GetMapping("/purchases/receipts")
    public PageResponse<GrnSummaryDto> receipts(@RequestParam(required = false) Long supplierId,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return purchases.receiptList(supplierId, page, size);
    }

    @GetMapping("/purchases/receipts/{id}")
    public GrnDto receipt(@PathVariable Long id) {
        return purchases.grn(id);
    }
}
