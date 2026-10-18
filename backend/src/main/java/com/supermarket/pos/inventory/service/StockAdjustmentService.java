package com.supermarket.pos.inventory.service;

import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.auth.entity.ApprovalAction;
import com.supermarket.pos.auth.service.ApprovalService;
import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.idempotency.IdempotencyRecord;
import com.supermarket.pos.common.idempotency.IdempotencyService;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.common.security.CurrentUser;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.common.sequence.SequenceService;
import com.supermarket.pos.common.web.PageRequests;
import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.inventory.dto.InventoryDtos.AdjustmentDto;
import com.supermarket.pos.inventory.dto.InventoryDtos.AdjustmentItemDto;
import com.supermarket.pos.inventory.dto.InventoryDtos.AdjustmentItemRequest;
import com.supermarket.pos.inventory.dto.InventoryDtos.AdjustmentRequest;
import com.supermarket.pos.inventory.entity.InventoryBalance;
import com.supermarket.pos.inventory.entity.MovementType;
import com.supermarket.pos.inventory.entity.StockAdjustment;
import com.supermarket.pos.inventory.entity.StockAdjustmentItem;
import com.supermarket.pos.inventory.repository.InventoryBalanceRepository;
import com.supermarket.pos.inventory.repository.StockAdjustmentRepository;
import com.supermarket.pos.inventory.service.InventoryService.Deduction;
import com.supermarket.pos.inventory.service.InventoryService.Receipt;
import com.supermarket.pos.inventory.service.InventoryService.Reference;
import com.supermarket.pos.products.entity.Product;
import com.supermarket.pos.products.service.ProductService;
import com.supermarket.pos.products.service.QuantityRules;
import com.supermarket.pos.store.service.SettingsService;
import com.supermarket.pos.store.service.StoreService;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manual stock changes as documents. There is deliberately no "set stock to X" operation:
 * every change is a signed quantity with a reason, posted to the ledger.
 */
@Service
public class StockAdjustmentService {

    public static final String REFERENCE_TYPE = "STOCK_ADJUSTMENT";
    private static final String OPERATION = "STOCK_ADJUSTMENT";

    private final StockAdjustmentRepository adjustments;
    private final InventoryBalanceRepository balances;
    private final InventoryService inventory;
    private final ProductService products;
    private final ApprovalService approvals;
    private final SettingsService settings;
    private final StoreService stores;
    private final SequenceService sequences;
    private final IdempotencyService idempotency;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate tx;

    public StockAdjustmentService(StockAdjustmentRepository adjustments, InventoryBalanceRepository balances,
                                  InventoryService inventory, ProductService products, ApprovalService approvals,
                                  SettingsService settings, StoreService stores, SequenceService sequences,
                                  IdempotencyService idempotency, CurrentUserProvider currentUser,
                                  AuditService audit, Clock clock, PlatformTransactionManager txManager) {
        this.adjustments = adjustments;
        this.balances = balances;
        this.inventory = inventory;
        this.products = products;
        this.approvals = approvals;
        this.settings = settings;
        this.stores = stores;
        this.sequences = sequences;
        this.idempotency = idempotency;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    /** Entry point: idempotent per Idempotency-Key. */
    public IdempotencyService.Outcome<AdjustmentDto> create(AdjustmentRequest request, String idempotencyKey) {
        CurrentUser user = currentUser.require();
        requirePermission(user, request.type());
        IdempotencyService.Request idem = idempotency.request(user.id(), OPERATION, idempotencyKey, request);
        return idempotency.execute(idem, () -> tx.execute(status -> post(request, idem)), this::get);
    }

    /** Runs inside the transaction opened by {@link #create}. */
    private AdjustmentDto post(AdjustmentRequest request, IdempotencyService.Request idem) {
        IdempotencyRecord record = idempotency.register(idem);
        CurrentUser user = currentUser.require();
        Long storeId = user.requireActiveStore();
        requirePermission(user, request.type());

        Map<Long, Product> productMap = products.loadForPricing(
                request.items().stream().map(AdjustmentItemRequest::productId).collect(Collectors.toSet()));
        Map<Long, InventoryBalance> current = balances.findByStoreIdAndProductIdIn(storeId, productMap.keySet())
                .stream().collect(Collectors.toMap(InventoryBalance::getProductId, Function.identity()));

        StockAdjustment adjustment = new StockAdjustment();
        adjustment.setStoreId(storeId);
        adjustment.setAdjustmentType(request.type());
        adjustment.setReason(request.reason().trim());
        adjustment.setCreatedBy(user.id());
        adjustment.setCreatedAt(clock.instant());
        BigDecimal totalValue = BigDecimal.ZERO;
        for (AdjustmentItemRequest line : request.items()) {
            Product product = productMap.get(line.productId());
            if (product == null) {
                throw ApiException.notFound("Product", line.productId());
            }
            BigDecimal signed = signedQuantity(request.type(), line.quantity());
            QuantityRules.validate(product, signed.abs());
            BigDecimal avg = current.containsKey(product.getId()) ? current.get(product.getId()).getAverageCost()
                    : product.getCostPrice();
            BigDecimal unitCost;
            if (request.type() == StockAdjustment.Type.OPENING_BALANCE) {
                unitCost = line.unitCost() != null ? line.unitCost() : product.getCostPrice();
            } else if (signed.signum() > 0) {
                unitCost = line.unitCost() != null ? line.unitCost() : avg;
            } else {
                unitCost = avg; // removals are valued at the current average cost
            }
            StockAdjustmentItem item = new StockAdjustmentItem();
            item.setAdjustment(adjustment);
            item.setProductId(product.getId());
            item.setBatchId(line.batchId());
            item.setQuantityChange(MoneyPolicy.quantity(signed));
            item.setUnitCost(MoneyPolicy.cost(unitCost));
            item.setReason(line.reason() == null || line.reason().isBlank() ? null : line.reason().trim());
            adjustment.getItems().add(item);
            totalValue = totalValue.add(signed.abs().multiply(unitCost));
        }
        adjustment.setTotalValue(MoneyPolicy.amount(totalValue));

        // Write-offs above the configured value need a second person unless the user may approve.
        boolean writeOff = request.type() == StockAdjustment.Type.WRITE_OFF_DAMAGED
                || request.type() == StockAdjustment.Type.WRITE_OFF_EXPIRED;
        BigDecimal threshold = settings.decimal(storeId, "inventory.writeOffApprovalThreshold", new BigDecimal("5000"));
        ApprovalService.ConsumedApproval approval = null;
        if (writeOff && adjustment.getTotalValue().compareTo(threshold) > 0 && !user.has("INVENTORY_APPROVE")) {
            if (request.approvalToken() == null || request.approvalToken().isBlank()) {
                throw new ApiException(ErrorCode.APPROVAL_REQUIRED, "This write-off (" + adjustment.getTotalValue()
                        + ") exceeds " + threshold + " and needs manager approval.",
                        Map.of("action", ApprovalAction.WRITE_OFF_OVER_LIMIT.name(),
                                "value", adjustment.getTotalValue().toPlainString()));
            }
            approval = approvals.consume(request.approvalToken(), ApprovalAction.WRITE_OFF_OVER_LIMIT, storeId,
                    user.id(), adjustment.getTotalValue());
            adjustment.setApprovedBy(approval.approverId());
        }

        String scope = stores.requireStore(storeId).getCode();
        adjustment.setAdjustmentNumber(sequences.nextNumber("ADJUSTMENT", "ADJ", scope, 6));
        StockAdjustment saved = adjustments.saveAndFlush(adjustment);

        Reference ref = new Reference(REFERENCE_TYPE, saved.getId(), user.id(), request.reason().trim());
        List<Deduction> outs = new ArrayList<>();
        List<Receipt> ins = new ArrayList<>();
        for (int i = 0; i < saved.getItems().size(); i++) {
            StockAdjustmentItem item = saved.getItems().get(i);
            AdjustmentItemRequest line = request.items().get(i);
            if (item.getQuantityChange().signum() < 0) {
                outs.add(new Deduction(item.getProductId(), item.getQuantityChange().negate(), item.getId(),
                        item.getBatchId()));
            } else {
                ins.add(new Receipt(item.getProductId(), item.getQuantityChange(), item.getUnitCost(),
                        line.batchNumber(), line.expiryDate(), item.getId(), item.getBatchId(), null));
            }
        }
        MovementType inType = request.type() == StockAdjustment.Type.OPENING_BALANCE
                ? MovementType.OPENING_BALANCE : MovementType.ADJUSTMENT_IN;
        MovementType outType = switch (request.type()) {
            case WRITE_OFF_DAMAGED -> MovementType.WRITE_OFF_DAMAGED;
            case WRITE_OFF_EXPIRED -> MovementType.WRITE_OFF_EXPIRED;
            default -> MovementType.ADJUSTMENT_OUT;
        };
        inventory.deduct(storeId, outType, ref, outs);
        inventory.receive(storeId, inType, ref, ins);

        if (approval != null) {
            approvals.linkReference(approval.grantId(), REFERENCE_TYPE, saved.getId());
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("type", request.type().name());
        details.put("totalValue", saved.getTotalValue().toPlainString());
        details.put("lines", saved.getItems().size());
        if (approval != null) {
            details.put("approvedBy", approval.approverId());
        }
        audit.record(AuditService.Entry.success("STOCK_ADJUSTED", "StockAdjustment", saved.getId(),
                saved.getAdjustmentNumber() + ": " + request.reason().trim()).withStore(storeId).withDetails(details));
        idempotency.complete(record, "STOCK_ADJUSTMENT", saved.getId(), 201);
        return toDto(saved, productMap);
    }

    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    @Transactional(readOnly = true)
    public AdjustmentDto get(Long id) {
        StockAdjustment adjustment = adjustments.findWithItemsById(id)
                .orElseThrow(() -> ApiException.notFound("Stock adjustment", id));
        currentUser.require().assertStoreAccess(adjustment.getStoreId(), "Stock adjustment", id);
        Map<Long, Product> productMap = products.loadForPricing(adjustment.getItems().stream()
                .map(StockAdjustmentItem::getProductId).collect(Collectors.toCollection(HashSet::new)));
        return toDto(adjustment, productMap);
    }

    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    @Transactional(readOnly = true)
    public PageResponse<AdjustmentDto> list(Integer page, Integer size) {
        Long storeId = currentUser.require().requireActiveStore();
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.of(adjustments.findByStoreId(storeId, pageable), a -> new AdjustmentDto(a.getId(),
                a.getAdjustmentNumber(), a.getAdjustmentType(), a.getReason(), a.getTotalValue(), a.getCreatedBy(),
                a.getApprovedBy(), a.getCreatedAt(), List.of()));
    }

    private static BigDecimal signedQuantity(StockAdjustment.Type type, BigDecimal quantity) {
        if (quantity == null || quantity.signum() == 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Quantities cannot be zero.");
        }
        return switch (type) {
            case OPENING_BALANCE -> {
                if (quantity.signum() < 0) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "Opening balances must be positive.");
                }
                yield quantity;
            }
            case WRITE_OFF_DAMAGED, WRITE_OFF_EXPIRED -> {
                if (quantity.signum() < 0) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED,
                            "Enter write-off quantities as positive numbers (the amount removed).");
                }
                yield quantity.negate();
            }
            case ADJUSTMENT -> quantity;
        };
    }

    private static void requirePermission(CurrentUser user, StockAdjustment.Type type) {
        String needed = switch (type) {
            case WRITE_OFF_DAMAGED, WRITE_OFF_EXPIRED -> "INVENTORY_WRITE_OFF";
            default -> "INVENTORY_ADJUST";
        };
        if (!user.has(needed)) {
            throw ApiException.forbidden("This adjustment type requires " + needed + ".");
        }
    }

    private static AdjustmentDto toDto(StockAdjustment a, Map<Long, Product> productMap) {
        List<AdjustmentItemDto> items = a.getItems().stream().map(i -> {
            Product p = productMap.get(i.getProductId());
            return new AdjustmentItemDto(i.getId(), i.getProductId(), p == null ? null : p.getName(),
                    p == null ? null : p.getSku(), i.getBatchId(), i.getQuantityChange(), i.getUnitCost(), i.getReason());
        }).toList();
        return new AdjustmentDto(a.getId(), a.getAdjustmentNumber(), a.getAdjustmentType(), a.getReason(),
                a.getTotalValue(), a.getCreatedBy(), a.getApprovedBy(), a.getCreatedAt(), items);
    }
}
