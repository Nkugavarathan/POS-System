package com.supermarket.pos.pos.dto;

import com.supermarket.pos.pos.entity.CashMovement;
import com.supermarket.pos.pos.entity.CashierShift;
import com.supermarket.pos.pos.entity.HeldCart;
import com.supermarket.pos.pos.entity.TransactionVoid;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class PosDtos {

    private PosDtos() {
    }

    public record OpenShiftRequest(@NotNull Long registerId,
                                   @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal openingFloat) {
    }

    public record ShiftDto(Long id, String shiftNumber, Long storeId, Long registerId, String registerCode,
                           Long cashierId, String cashierName, CashierShift.Status status, Instant openedAt,
                           BigDecimal openingFloat, Instant closedAt, BigDecimal expectedCash, BigDecimal countedCash,
                           BigDecimal variance, long version) {
    }

    public record CashMovementRequest(@NotNull CashMovement.Type type,
                                      @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
                                      @NotBlank @Size(max = 255) String reason,
                                      String approvalToken) {
    }

    public record CashMovementDto(Long id, CashMovement.Type type, BigDecimal amount, String reason, Long performedBy,
                                  Long approvedBy, Instant createdAt) {
    }

    public record TenderTotal(String tenderType, BigDecimal amount, long count) {
    }

    /** X-report (open shift) / Z-report (closed shift). */
    public record ShiftSummaryDto(ShiftDto shift, BigDecimal cashSales, BigDecimal cashRefunds, BigDecimal payIns,
                                  BigDecimal payOuts, BigDecimal cashDrops, BigDecimal expectedCash,
                                  List<TenderTotal> tenderTotals, long salesCount, long returnsCount, long voidsCount,
                                  BigDecimal salesTotal, BigDecimal refundsTotal, List<CashMovementDto> cashMovements) {
    }

    public record CloseShiftRequest(@NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal countedCash,
                                    Map<String, Integer> denominations,
                                    @Size(max = 500) String notes,
                                    String approvalToken) {
    }

    public record HeldItem(@NotNull Long productId,
                           @NotNull @DecimalMin("0.001") @Digits(integer = 12, fraction = 3) BigDecimal quantity) {
    }

    public record HoldCartRequest(@NotNull Long registerId, Long customerId, @Size(max = 60) String label,
                                  @NotEmpty @Size(max = 300) List<@Valid HeldItem> items) {
    }

    public record HeldCartDto(Long id, String label, Long customerId, HeldCart.Status status, Long heldBy,
                              String heldByName, Instant heldAt, int itemCount, List<HeldItem> items) {
    }

    public record VoidItem(@NotNull Long productId, @Size(max = 150) String name,
                           @NotNull @DecimalMin("0.001") BigDecimal quantity,
                           @NotNull @DecimalMin("0.00") BigDecimal amount) {
    }

    public record VoidRequest(@NotNull Long registerId, @NotNull TransactionVoid.Type type,
                              @NotBlank @Size(max = 30) String reasonCode, @Size(max = 255) String reasonText,
                              @NotEmpty @Size(max = 300) List<@Valid VoidItem> items, String approvalToken) {
    }

    public record VoidDto(Long id, TransactionVoid.Type type, String reasonCode, BigDecimal estimatedAmount,
                          Long approvedBy, Instant createdAt) {
    }
}
