package com.supermarket.pos.returns.dto;

import com.supermarket.pos.returns.entity.ReturnItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ReturnDtos {

    private ReturnDtos() {
    }

    public record ReturnLineRequest(@NotNull Long saleItemId,
                                    @NotNull @DecimalMin("0.001") @Digits(integer = 12, fraction = 3) BigDecimal quantity,
                                    @NotNull ReturnItem.Condition condition,
                                    boolean restock,
                                    @Size(max = 255) String reason) {
    }

    public record RefundRequest(@NotBlank String tenderType,
                                @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
                                @Size(max = 80) String reference,
                                @Pattern(regexp = "^\\d{4}$") String cardLast4) {
    }

    public record ReturnRequest(@NotBlank @Size(max = 50) String originalReceiptNumber,
                                @NotNull Long registerId,
                                @NotBlank @Size(max = 40) String reasonCode,
                                @Size(max = 500) String notes,
                                @NotEmpty @Size(max = 300) List<@Valid ReturnLineRequest> items,
                                @NotEmpty @Size(max = 5) List<@Valid RefundRequest> refunds,
                                String approvalToken) {
    }

    /** Server-computed refund preview so the cashier can choose tenders before submitting. */
    public record PreviewRequest(@NotBlank String originalReceiptNumber,
                                 @NotEmpty List<@Valid ReturnLineRequest> items) {
    }

    public record TenderAllowance(String tenderType, BigDecimal paid, BigDecimal alreadyRefunded, BigDecimal available) {
    }

    public record PreviewDto(BigDecimal refundTotal, BigDecimal taxTotal, List<ReturnItemDto> items,
                             List<TenderAllowance> tenders, boolean approvalRequired, boolean withinWindow,
                             long loyaltyPointsToReverse) {
    }

    public record ReturnItemDto(Long saleItemId, Long productId, String name, BigDecimal quantity, BigDecimal refundAmount,
                                BigDecimal taxAmount, ReturnItem.Condition condition, boolean restock) {
    }

    public record RefundDto(String tenderType, BigDecimal amount, String reference, String cardLast4, Long loyaltyPoints) {
    }

    public record ReturnDto(Long id, String returnNumber, Long originalSaleId, String originalReceiptNumber,
                            String reasonCode, String notes, BigDecimal refundTotal, BigDecimal taxTotal,
                            List<ReturnItemDto> items, List<RefundDto> refunds, String processedBy, String approvedBy,
                            long loyaltyPointsReversed, Instant createdAt) {
    }
}
