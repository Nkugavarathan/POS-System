package com.supermarket.pos.inventory.dto;

import com.supermarket.pos.inventory.entity.MovementType;
import com.supermarket.pos.inventory.entity.StockAdjustment;
import com.supermarket.pos.inventory.entity.StockAlert;
import com.supermarket.pos.inventory.entity.StockCount;
import com.supermarket.pos.products.entity.UnitOfMeasure;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    /** {@code averageCost} and {@code stockValue} are null without PRODUCT_VIEW_COST. */
    public record InventoryRowDto(Long productId, String sku, String name, String categoryName,
                                  UnitOfMeasure unitOfMeasure, boolean active, BigDecimal quantityOnHand,
                                  BigDecimal reorderLevel, boolean lowStock, BigDecimal averageCost,
                                  BigDecimal stockValue, boolean trackBatches, Instant lastMovementAt) {
    }

    public record BatchDto(Long id, String batchNumber, LocalDate expiryDate, Instant receivedAt, BigDecimal unitCost,
                           BigDecimal quantityReceived, BigDecimal quantityRemaining, String status, boolean expired,
                           Long daysToExpiry) {
    }

    public record MovementDto(Long id, Long productId, String productName, String sku, MovementType type,
                              BigDecimal quantityChange, BigDecimal quantityBefore, BigDecimal quantityAfter,
                              BigDecimal unitCost, Long batchId, String referenceType, Long referenceId,
                              String reason, Long performedBy, Instant createdAt) {
    }

    public record InventoryDetailDto(InventoryRowDto stock, List<BatchDto> batches, List<MovementDto> recentMovements) {
    }

    public record ExpiringBatchDto(Long batchId, Long productId, String productName, String sku, String batchNumber,
                                   LocalDate expiryDate, long daysToExpiry, BigDecimal quantityRemaining,
                                   BigDecimal value) {
    }

    public record AdjustmentItemRequest(
            @NotNull Long productId,
            @NotNull @Digits(integer = 12, fraction = 3) BigDecimal quantity,
            @DecimalMin("0.0000") @Digits(integer = 11, fraction = 4) BigDecimal unitCost,
            Long batchId,
            @Size(max = 50) String batchNumber,
            LocalDate expiryDate,
            @Size(max = 255) String reason) {
    }

    /**
     * {@code quantity} semantics by type: OPENING_BALANCE and write-offs take positive
     * quantities (added / removed); ADJUSTMENT takes a signed change (+ found, - missing).
     */
    public record AdjustmentRequest(
            @NotNull StockAdjustment.Type type,
            @NotBlank @Size(max = 255) String reason,
            @NotEmpty @Size(max = 200) List<@Valid AdjustmentItemRequest> items,
            String approvalToken) {
    }

    public record AdjustmentItemDto(Long id, Long productId, String productName, String sku, Long batchId,
                                    BigDecimal quantityChange, BigDecimal unitCost, String reason) {
    }

    public record AdjustmentDto(Long id, String adjustmentNumber, StockAdjustment.Type type, String reason,
                                BigDecimal totalValue, Long createdBy, Long approvedBy, Instant createdAt,
                                List<AdjustmentItemDto> items) {
    }

    public record CreateCountRequest(Long categoryId, @Size(max = 255) String notes) {
    }

    public record CountEntry(@NotNull Long productId,
                             @DecimalMin("0.000") @Digits(integer = 12, fraction = 3) BigDecimal countedQuantity) {
    }

    public record RecordCountRequest(@NotNull @Size(max = 5000) List<@Valid CountEntry> entries, @NotNull Long version) {
    }

    public record CountItemDto(Long id, Long productId, String sku, String name, UnitOfMeasure unitOfMeasure,
                               BigDecimal systemQuantity, BigDecimal countedQuantity, BigDecimal quantityAtPost,
                               BigDecimal variance) {
    }

    public record CountDto(Long id, String countNumber, StockCount.Status status, Long categoryId, String notes,
                           Long createdBy, Instant createdAt, Instant submittedAt, Instant postedAt, int itemCount,
                           int countedCount, long version, List<CountItemDto> items) {
    }

    public record AlertDto(Long id, Long productId, String productName, String sku, Long batchId,
                           StockAlert.Type type, String message, Instant createdAt) {
    }
}
