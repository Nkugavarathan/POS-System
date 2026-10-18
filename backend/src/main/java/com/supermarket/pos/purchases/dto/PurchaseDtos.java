package com.supermarket.pos.purchases.dto;

import com.supermarket.pos.purchases.entity.PurchaseOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PurchaseDtos {

    private PurchaseDtos() {
    }

    public record SupplierDto(Long id, String code, String name, String contactPerson, String phone, String email,
                              String address, String taxRegistrationNo, int paymentTermsDays, String notes, boolean active,
                              long version) {
    }

    public record SupplierRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9_-]{2,20}$", message = "2-20 upper-case letters, digits, _ or -") String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 120) String contactPerson,
            @Size(max = 30) String phone,
            @Email @Size(max = 120) String email,
            @Size(max = 300) String address,
            @Size(max = 40) String taxRegistrationNo,
            @Min(0) int paymentTermsDays,
            @Size(max = 500) String notes,
            boolean active,
            Long version) {
    }

    public record PoLineRequest(@NotNull Long productId,
                                @NotNull @DecimalMin("0.001") @Digits(integer = 12, fraction = 3) BigDecimal quantity,
                                @NotNull @DecimalMin("0.0000") @Digits(integer = 11, fraction = 4) BigDecimal unitCost) {
    }

    public record PoRequest(@NotNull Long supplierId, LocalDate expectedDate, @Size(max = 500) String notes,
                            @NotEmpty @Size(max = 300) List<@Valid PoLineRequest> items, Long version) {
    }

    public record PoLineDto(Long id, Long productId, String sku, String name, String unitOfMeasure,
                            BigDecimal quantityOrdered, BigDecimal quantityReceived, BigDecimal outstanding,
                            BigDecimal expectedUnitCost, BigDecimal lineTotal, boolean trackBatches, boolean trackExpiry) {
    }

    public record PoDto(Long id, String poNumber, Long supplierId, String supplierName, PurchaseOrder.Status status,
                        LocalDate orderDate, LocalDate expectedDate, BigDecimal expectedTotal, String notes,
                        String createdBy, String approvedBy, Instant createdAt, long version, List<PoLineDto> items,
                        List<GrnSummaryDto> receipts) {
    }

    public record ReceiveLineRequest(Long purchaseOrderItemId,
                                     Long productId,
                                     @NotNull @DecimalMin("0.001") @Digits(integer = 12, fraction = 3) BigDecimal quantity,
                                     @NotNull @DecimalMin("0.0000") @Digits(integer = 11, fraction = 4) BigDecimal unitCost,
                                     @Size(max = 50) String batchNumber,
                                     LocalDate expiryDate) {
    }

    public record ReceiveRequest(Long supplierId, @Size(max = 60) String supplierInvoiceNo, @Size(max = 500) String notes,
                                 @NotEmpty @Size(max = 300) List<@Valid ReceiveLineRequest> items) {
    }

    public record GrnLineDto(Long id, Long productId, String name, BigDecimal quantity, BigDecimal unitCost,
                             String batchNumber, LocalDate expiryDate) {
    }

    public record GrnSummaryDto(Long id, String grnNumber, Instant receivedAt, BigDecimal totalCost, String supplierInvoiceNo) {
    }

    public record GrnDto(Long id, String grnNumber, Long supplierId, String supplierName, Long purchaseOrderId,
                         String poNumber, String supplierInvoiceNo, Instant receivedAt, String receivedBy,
                         BigDecimal totalCost, String notes, List<GrnLineDto> items) {
    }
}
