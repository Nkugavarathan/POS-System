package com.supermarket.pos.products.controller;

import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.products.dto.ProductDtos.BarcodeRequest;
import com.supermarket.pos.products.dto.ProductDtos.PosProductDto;
import com.supermarket.pos.products.dto.ProductDtos.ProductDto;
import com.supermarket.pos.products.dto.ProductDtos.ProductRequest;
import com.supermarket.pos.products.dto.ProductDtos.ProductStatusRequest;
import com.supermarket.pos.products.dto.ProductDtos.ProductSummaryDto;
import com.supermarket.pos.products.dto.ProductDtos.PromotionDto;
import com.supermarket.pos.products.dto.ProductDtos.PromotionRequest;
import com.supermarket.pos.products.dto.ProductDtos.TaxRateDto;
import com.supermarket.pos.products.dto.ProductDtos.TaxRateRequest;
import com.supermarket.pos.products.service.ProductService;
import com.supermarket.pos.products.service.ProductService.SearchCriteria;
import com.supermarket.pos.products.service.TaxRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Products", description = "Catalog, barcodes, promotions and tax rates")
@RestController
@RequestMapping("/api/v1")
public class ProductController {

    private final ProductService products;
    private final TaxRateService taxRates;

    public ProductController(ProductService products, TaxRateService taxRates) {
        this.products = products;
        this.taxRates = taxRates;
    }

    @Operation(summary = "Search products (q matches name, SKU prefix, brand or an exact barcode)")
    @GetMapping("/products")
    public PageResponse<ProductSummaryDto> search(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) Long categoryId,
                                                  @RequestParam(required = false) Boolean active,
                                                  @RequestParam(required = false) String brand,
                                                  @RequestParam(defaultValue = "false") boolean lowStock,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size,
                                                  @RequestParam(required = false) List<String> sort) {
        return products.search(new SearchCriteria(q, categoryId, active, brand, lowStock, page, size, sort));
    }

    @Operation(summary = "Cashier type-ahead: active products with display price and stock")
    @GetMapping("/products/pos-search")
    public List<PosProductDto> posSearch(@RequestParam String q, @RequestParam(defaultValue = "12") int limit) {
        return products.posSearch(q, limit);
    }

    @GetMapping("/products/brands")
    public List<String> brands() {
        return products.brands();
    }

    @Operation(summary = "Look up a scanned or typed barcode (normalized server-side)")
    @GetMapping("/products/barcode/{barcode}")
    public PosProductDto byBarcode(@PathVariable String barcode, @RequestParam(required = false) String format) {
        return products.lookupByBarcode(barcode, format);
    }

    @GetMapping("/products/{id}")
    public ProductDto get(@PathVariable Long id) {
        return products.get(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto create(@Valid @RequestBody ProductRequest request) {
        return products.create(request);
    }

    @PutMapping("/products/{id}")
    public ProductDto update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return products.update(id, request);
    }

    @PatchMapping("/products/{id}/status")
    public ProductDto setStatus(@PathVariable Long id, @Valid @RequestBody ProductStatusRequest request) {
        return products.setActive(id, request.active(), request.version());
    }

    @PostMapping("/products/{id}/barcodes")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto addBarcode(@PathVariable Long id, @Valid @RequestBody BarcodeRequest request) {
        return products.addBarcode(id, request);
    }

    @DeleteMapping("/products/{id}/barcodes/{barcodeId}")
    public ProductDto removeBarcode(@PathVariable Long id, @PathVariable Long barcodeId) {
        return products.removeBarcode(id, barcodeId);
    }

    @PostMapping("/products/{id}/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionDto createPromotion(@PathVariable Long id, @Valid @RequestBody PromotionRequest request) {
        return products.createPromotion(id, request);
    }

    @PutMapping("/products/{id}/promotions/{promotionId}")
    public PromotionDto updatePromotion(@PathVariable Long id, @PathVariable Long promotionId,
                                        @Valid @RequestBody PromotionRequest request) {
        return products.updatePromotion(id, promotionId, request);
    }

    @GetMapping("/tax-rates")
    public List<TaxRateDto> taxRates() {
        return taxRates.list();
    }

    @PostMapping("/tax-rates")
    @ResponseStatus(HttpStatus.CREATED)
    public TaxRateDto createTaxRate(@Valid @RequestBody TaxRateRequest request) {
        return taxRates.create(request);
    }

    @PutMapping("/tax-rates/{id}")
    public TaxRateDto updateTaxRate(@PathVariable Long id, @Valid @RequestBody TaxRateRequest request) {
        return taxRates.update(id, request);
    }
}
