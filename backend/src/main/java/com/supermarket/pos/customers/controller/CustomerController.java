package com.supermarket.pos.customers.controller;

import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.customers.dto.CustomerDtos.CustomerDto;
import com.supermarket.pos.customers.dto.CustomerDtos.CustomerRequest;
import com.supermarket.pos.customers.dto.CustomerDtos.CustomerSummaryDto;
import com.supermarket.pos.customers.dto.CustomerDtos.LoyaltyAdjustmentRequest;
import com.supermarket.pos.customers.dto.CustomerDtos.LoyaltyTransactionDto;
import com.supermarket.pos.customers.dto.CustomerDtos.PosCustomerDto;
import com.supermarket.pos.customers.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

@Tag(name = "Customers", description = "Customers and loyalty")
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    public PageResponse<CustomerSummaryDto> search(@RequestParam(required = false) String q,
                                                   @RequestParam(required = false) Boolean active,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size,
                                                   @RequestParam(required = false) List<String> sort) {
        return customers.search(q, active, page, size, sort);
    }

    @Operation(summary = "Cashier lookup by loyalty card number or phone")
    @GetMapping("/lookup")
    public PosCustomerDto lookup(@RequestParam String value) {
        return customers.lookup(value);
    }

    @GetMapping("/{id}")
    public CustomerDto get(@PathVariable Long id) {
        return customers.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDto create(@Valid @RequestBody CustomerRequest request) {
        return customers.create(request);
    }

    @PutMapping("/{id}")
    public CustomerDto update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customers.update(id, request);
    }

    @GetMapping("/{id}/loyalty")
    public PageResponse<LoyaltyTransactionDto> loyalty(@PathVariable Long id,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return customers.loyaltyHistory(id, page, size);
    }

    @PostMapping("/{id}/loyalty/adjustments")
    public CustomerDto adjust(@PathVariable Long id, @Valid @RequestBody LoyaltyAdjustmentRequest request) {
        return customers.adjustPoints(id, request.points(), request.reason());
    }
}
