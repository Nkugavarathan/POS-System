package com.supermarket.pos.purchases.service;

import com.supermarket.pos.audit.service.AuditService;
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
import com.supermarket.pos.inventory.entity.MovementType;
import com.supermarket.pos.inventory.service.InventoryService;
import com.supermarket.pos.inventory.service.InventoryService.Receipt;
import com.supermarket.pos.inventory.service.InventoryService.Reference;
import com.supermarket.pos.products.entity.Product;
import com.supermarket.pos.products.service.ProductService;
import com.supermarket.pos.products.service.QuantityRules;
import com.supermarket.pos.purchases.dto.PurchaseDtos.GrnDto;
import com.supermarket.pos.purchases.dto.PurchaseDtos.GrnLineDto;
import com.supermarket.pos.purchases.dto.PurchaseDtos.GrnSummaryDto;
import com.supermarket.pos.purchases.dto.PurchaseDtos.PoDto;
import com.supermarket.pos.purchases.dto.PurchaseDtos.PoLineDto;
import com.supermarket.pos.purchases.dto.PurchaseDtos.PoLineRequest;
import com.supermarket.pos.purchases.dto.PurchaseDtos.PoRequest;
import com.supermarket.pos.purchases.dto.PurchaseDtos.ReceiveLineRequest;
import com.supermarket.pos.purchases.dto.PurchaseDtos.ReceiveRequest;
import com.supermarket.pos.purchases.entity.GoodsReceipt;
import com.supermarket.pos.purchases.entity.GoodsReceiptItem;
import com.supermarket.pos.purchases.entity.PurchaseOrder;
import com.supermarket.pos.purchases.entity.PurchaseOrderItem;
import com.supermarket.pos.purchases.repository.GoodsReceiptRepository;
import com.supermarket.pos.purchases.repository.PurchaseOrderRepository;
import com.supermarket.pos.store.service.SettingsService;
import com.supermarket.pos.store.service.StoreService;
import com.supermarket.pos.suppliers.entity.Supplier;
import com.supermarket.pos.suppliers.service.SupplierService;
import com.supermarket.pos.users.service.UserAccountService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Purchase orders and goods receipts (W9). Stock and moving-average cost change only through
 * received goods; ordered-but-not-received quantities never touch inventory.
 */
@Service
public class PurchaseService {

    public static final String GRN_REFERENCE = "GOODS_RECEIPT";

    private final PurchaseOrderRepository orders;
    private final GoodsReceiptRepository receipts;
    private final SupplierService suppliers;
    private final ProductService products;
    private final InventoryService inventory;
    private final SettingsService settings;
    private final StoreService stores;
    private final SequenceService sequences;
    private final IdempotencyService idempotency;
    private final UserAccountService users;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate tx;

    public PurchaseService(PurchaseOrderRepository orders, GoodsReceiptRepository receipts, SupplierService suppliers,
                           ProductService products, InventoryService inventory, SettingsService settings,
                           StoreService stores, SequenceService sequences, IdempotencyService idempotency,
                           UserAccountService users, CurrentUserProvider currentUser, AuditService audit, Clock clock,
                           PlatformTransactionManager txManager) {
        this.orders = orders;
        this.receipts = receipts;
        this.suppliers = suppliers;
        this.products = products;
        this.inventory = inventory;
        this.settings = settings;
        this.stores = stores;
        this.sequences = sequences;
        this.idempotency = idempotency;
        this.users = users;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    // ----- purchase orders ---------------------------------------------------------------------

    @PreAuthorize("hasAnyAuthority('PURCHASE_ORDER_MANAGE','INVENTORY_RECEIVE')")
    @Transactional(readOnly = true)
    public PageResponse<PoDto> list(Long supplierId, PurchaseOrder.Status status, String q, Integer page, Integer size) {
        Long storeId = currentUser.require().requireActiveStore();
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = orders.search(storeId, supplierId, status, q == null || q.isBlank() ? null : q.trim(), pageable);
        Map<Long, String> supplierNames = suppliers.names(result.getContent().stream().map(PurchaseOrder::getSupplierId).collect(Collectors.toSet()));
        return PageResponse.of(result, p -> new PoDto(p.getId(), p.getPoNumber(), p.getSupplierId(), supplierNames.get(p.getSupplierId()),
                p.getStatus(), p.getOrderDate(), p.getExpectedDate(), p.getExpectedTotal(), p.getNotes(), null, null,
                p.getCreatedAt(), p.getVersion(), List.of(), List.of()));
    }

    @PreAuthorize("hasAnyAuthority('PURCHASE_ORDER_MANAGE','INVENTORY_RECEIVE')")
    @Transactional(readOnly = true)
    public PoDto get(Long id) {
        return toDto(load(id));
    }

    @PreAuthorize("hasAuthority('PURCHASE_ORDER_MANAGE')")
    @Transactional
    public PoDto create(PoRequest request) {
        CurrentUser user = currentUser.require();
        Long storeId = user.requireActiveStore();
        Supplier supplier = activeSupplier(request.supplierId());
        PurchaseOrder po = new PurchaseOrder();
        po.setStoreId(storeId);
        po.setSupplierId(supplier.getId());
        po.setCreatedBy(user.id());
        po.setPoNumber(sequences.nextNumber("PURCHASE_ORDER", "PO", stores.requireStore(storeId).getCode(), 6));
        applyLines(po, request);
        PurchaseOrder saved = orders.saveAndFlush(po);
        audit.record(AuditService.Entry.success("PO_CREATED", "PurchaseOrder", saved.getId(), saved.getPoNumber()).withStore(storeId));
        return toDto(saved);
    }

    @PreAuthorize("hasAuthority('PURCHASE_ORDER_MANAGE')")
    @Transactional
    public PoDto update(Long id, PoRequest request) {
        PurchaseOrder po = load(id);
        if (request.version() == null || po.getVersion() != request.version()) {
            throw new OptimisticLockingFailureException("PO changed");
        }
        if (po.getStatus() != PurchaseOrder.Status.DRAFT) {
            throw ApiException.invalidState("Only draft purchase orders can be edited.");
        }
        po.setSupplierId(activeSupplier(request.supplierId()).getId());
        applyLines(po, request);
        audit.record(AuditService.Entry.success("PO_UPDATED", "PurchaseOrder", id, po.getPoNumber()).withStore(po.getStoreId()));
        return toDto(orders.saveAndFlush(po));
    }

    /** DRAFT → ORDERED. Large orders need PURCHASE_ORDER_APPROVE. */
    @PreAuthorize("hasAuthority('PURCHASE_ORDER_MANAGE')")
    @Transactional
    public PoDto submit(Long id) {
        CurrentUser user = currentUser.require();
        PurchaseOrder po = orders.lockById(id).orElseThrow(() -> ApiException.notFound("Purchase order", id));
        user.assertStoreAccess(po.getStoreId(), "Purchase order", id);
        if (po.getStatus() != PurchaseOrder.Status.DRAFT) {
            throw ApiException.invalidState("Only draft purchase orders can be submitted.");
        }
        BigDecimal threshold = settings.decimal(po.getStoreId(), "purchasing.poApprovalThreshold", new BigDecimal("100000"));
        if (po.getExpectedTotal().compareTo(threshold) > 0 && !user.has("PURCHASE_ORDER_APPROVE")) {
            throw new ApiException(ErrorCode.APPROVAL_REQUIRED, "Orders above " + threshold
                    + " must be submitted by someone with purchase-order approval rights.");
        }
        po.setStatus(PurchaseOrder.Status.ORDERED);
        po.setOrderDate(LocalDate.now(clock));
        po.setApprovedBy(user.id());
        po.setApprovedAt(clock.instant());
        audit.record(AuditService.Entry.success("PO_ORDERED", "PurchaseOrder", id, po.getPoNumber() + " " + po.getExpectedTotal())
                .withStore(po.getStoreId()));
        return toDto(load(id));
    }

    @PreAuthorize("hasAuthority('PURCHASE_ORDER_MANAGE')")
    @Transactional
    public PoDto close(Long id) {
        PurchaseOrder po = lockForTransition(id);
        if (po.getStatus() != PurchaseOrder.Status.PARTIALLY_RECEIVED && po.getStatus() != PurchaseOrder.Status.ORDERED) {
            throw ApiException.invalidState("Only open orders can be closed.");
        }
        po.setStatus(po.getItems().stream().anyMatch(i -> i.getQuantityReceived().signum() > 0)
                ? PurchaseOrder.Status.CLOSED : PurchaseOrder.Status.CANCELLED);
        audit.record(AuditService.Entry.success("PO_CLOSED", "PurchaseOrder", id, po.getPoNumber() + " (short delivery accepted)")
                .withStore(po.getStoreId()));
        return toDto(load(id));
    }

    @PreAuthorize("hasAuthority('PURCHASE_ORDER_MANAGE')")
    @Transactional
    public PoDto cancel(Long id) {
        PurchaseOrder po = lockForTransition(id);
        if (po.getStatus() != PurchaseOrder.Status.DRAFT && po.getStatus() != PurchaseOrder.Status.ORDERED) {
            throw ApiException.invalidState("Orders with received goods cannot be cancelled; close them instead.");
        }
        po.setStatus(PurchaseOrder.Status.CANCELLED);
        audit.record(AuditService.Entry.success("PO_CANCELLED", "PurchaseOrder", id, po.getPoNumber()).withStore(po.getStoreId()));
        return toDto(load(id));
    }

    // ----- receiving -----------------------------------------------------------------------------

    /** Receive against a PO ({@code poId}) or, when null, an ad-hoc delivery from {@code request.supplierId}. */
    public IdempotencyService.Outcome<GrnDto> receive(Long poId, ReceiveRequest request, String key) {
        CurrentUser user = currentUser.require();
        if (!user.has("INVENTORY_RECEIVE")) {
            throw ApiException.forbidden("Receiving goods requires INVENTORY_RECEIVE.");
        }
        IdempotencyService.Request idem = idempotency.request(user.id(), "GOODS_RECEIPT", key,
                Map.of("po", poId == null ? 0 : poId, "body", request));
        return idempotency.execute(idem, () -> tx.execute(s -> doReceive(poId, request, idem, user)), this::loadGrn);
    }

    private GrnDto doReceive(Long poId, ReceiveRequest request, IdempotencyService.Request idem, CurrentUser user) {
        IdempotencyRecord record = idempotency.register(idem);
        Long storeId = user.requireActiveStore();
        PurchaseOrder po = null;
        Map<Long, PurchaseOrderItem> poItems = Map.of();
        Long supplierId;
        if (poId != null) {
            po = orders.lockById(poId).orElseThrow(() -> ApiException.notFound("Purchase order", poId));
            user.assertStoreAccess(po.getStoreId(), "Purchase order", poId);
            if (po.getStatus() != PurchaseOrder.Status.ORDERED && po.getStatus() != PurchaseOrder.Status.PARTIALLY_RECEIVED) {
                throw ApiException.invalidState("This order is " + po.getStatus().name().toLowerCase() + "; it cannot be received.");
            }
            poItems = po.getItems().stream().collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity()));
            supplierId = po.getSupplierId();
        } else {
            if (request.supplierId() == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose the supplier for this delivery.");
            }
            supplierId = activeSupplier(request.supplierId()).getId();
        }

        Set<Long> productIds = new HashSet<>();
        for (ReceiveLineRequest line : request.items()) {
            if (po != null) {
                PurchaseOrderItem item = poItems.get(line.purchaseOrderItemId());
                if (item == null) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "Line " + line.purchaseOrderItemId() + " is not on this order.");
                }
                productIds.add(item.getProductId());
            } else {
                if (line.productId() == null) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "Each line needs a product.");
                }
                productIds.add(line.productId());
            }
        }
        Map<Long, Product> productMap = products.loadForPricing(productIds);

        GoodsReceipt grn = new GoodsReceipt();
        grn.setStoreId(storeId);
        grn.setSupplierId(supplierId);
        grn.setPurchaseOrderId(po == null ? null : po.getId());
        grn.setSupplierInvoiceNo(request.supplierInvoiceNo());
        grn.setNotes(request.notes());
        grn.setReceivedAt(clock.instant());
        grn.setReceivedBy(user.id());
        grn.setGrnNumber(sequences.nextNumber("GOODS_RECEIPT", "GRN", stores.requireStore(storeId).getCode(), 6));
        BigDecimal total = BigDecimal.ZERO;
        Map<Long, BigDecimal> receivedPerPoItem = new java.util.HashMap<>();
        for (ReceiveLineRequest line : request.items()) {
            PurchaseOrderItem poItem = po == null ? null : poItems.get(line.purchaseOrderItemId());
            Long productId = poItem != null ? poItem.getProductId() : line.productId();
            Product product = productMap.get(productId);
            if (product == null) {
                throw ApiException.notFound("Product", productId);
            }
            BigDecimal qty = QuantityRules.validate(product, line.quantity());
            if (poItem != null) {
                BigDecimal sum = receivedPerPoItem.merge(poItem.getId(), qty, BigDecimal::add);
                if (sum.compareTo(poItem.outstanding()) > 0) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, product.getName() + ": only "
                            + poItem.outstanding().stripTrailingZeros().toPlainString() + " outstanding on this order."
                            + " Record extra goods as a separate delivery.");
                }
            }
            if (product.isTrackExpiry() && line.expiryDate() == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, product.getName() + " needs an expiry date.");
            }
            GoodsReceiptItem item = new GoodsReceiptItem();
            item.setGoodsReceipt(grn);
            item.setPurchaseOrderItemId(poItem == null ? null : poItem.getId());
            item.setProductId(productId);
            item.setQuantityReceived(qty);
            item.setUnitCost(MoneyPolicy.cost(line.unitCost()));
            item.setBatchNumber(line.batchNumber() == null || line.batchNumber().isBlank() ? null : line.batchNumber().trim());
            item.setExpiryDate(line.expiryDate());
            grn.getItems().add(item);
            total = total.add(qty.multiply(item.getUnitCost()));
        }
        grn.setTotalCost(MoneyPolicy.amount(total));
        GoodsReceipt saved = receipts.saveAndFlush(grn);

        // Ledger: receive every line (creates/extends batches, updates moving average cost).
        List<Receipt> ledger = saved.getItems().stream().map(i -> new Receipt(i.getProductId(), i.getQuantityReceived(),
                i.getUnitCost(), i.getBatchNumber(), i.getExpiryDate(), i.getId(), null, i.getId())).toList();
        var received = inventory.receive(storeId, MovementType.PURCHASE_RECEIPT,
                new Reference(GRN_REFERENCE, saved.getId(), user.id(), saved.getGrnNumber()), ledger);
        for (int i = 0; i < received.size(); i++) {
            saved.getItems().get(i).setBatchId(received.get(i).batchId());
        }

        if (po != null) {
            for (GoodsReceiptItem i : saved.getItems()) {
                PurchaseOrderItem poItem = poItems.get(i.getPurchaseOrderItemId());
                poItem.setQuantityReceived(poItem.getQuantityReceived().add(i.getQuantityReceived()));
            }
            boolean complete = po.getItems().stream().allMatch(i -> i.outstanding().signum() == 0);
            po.setStatus(complete ? PurchaseOrder.Status.RECEIVED : PurchaseOrder.Status.PARTIALLY_RECEIVED);
        }
        audit.record(AuditService.Entry.success("GOODS_RECEIVED", "GoodsReceipt", saved.getId(),
                saved.getGrnNumber() + (po != null ? " for " + po.getPoNumber() : " (no PO)") + " " + saved.getTotalCost())
                .withStore(storeId));
        idempotency.complete(record, "GOODS_RECEIPT", saved.getId(), 201);
        return toGrnDto(saved);
    }

    @PreAuthorize("hasAnyAuthority('INVENTORY_RECEIVE','PURCHASE_ORDER_MANAGE','INVENTORY_VIEW')")
    @Transactional(readOnly = true)
    public PageResponse<GrnSummaryDto> receiptList(Long supplierId, Integer page, Integer size) {
        Long storeId = currentUser.require().requireActiveStore();
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "receivedAt"));
        return PageResponse.of(receipts.search(storeId, supplierId, pageable),
                g -> new GrnSummaryDto(g.getId(), g.getGrnNumber(), g.getReceivedAt(), g.getTotalCost(), g.getSupplierInvoiceNo()));
    }

    @PreAuthorize("hasAnyAuthority('INVENTORY_RECEIVE','PURCHASE_ORDER_MANAGE','INVENTORY_VIEW')")
    @Transactional(readOnly = true)
    public GrnDto grn(Long id) {
        GoodsReceipt g = receipts.findWithItemsById(id).orElseThrow(() -> ApiException.notFound("Goods receipt", id));
        currentUser.require().assertStoreAccess(g.getStoreId(), "Goods receipt", id);
        return toGrnDto(g);
    }

    // ----- helpers -------------------------------------------------------------------------------

    private GrnDto loadGrn(Long id) {
        return toGrnDto(receipts.findWithItemsById(id).orElseThrow());
    }

    private PurchaseOrder load(Long id) {
        PurchaseOrder po = orders.findWithItemsById(id).orElseThrow(() -> ApiException.notFound("Purchase order", id));
        currentUser.require().assertStoreAccess(po.getStoreId(), "Purchase order", id);
        return po;
    }

    private PurchaseOrder lockForTransition(Long id) {
        PurchaseOrder po = orders.lockById(id).orElseThrow(() -> ApiException.notFound("Purchase order", id));
        currentUser.require().assertStoreAccess(po.getStoreId(), "Purchase order", id);
        po.getItems().size();
        return po;
    }

    private Supplier activeSupplier(Long id) {
        Supplier s = suppliers.require(id);
        if (!s.isActive()) {
            throw ApiException.rule("Supplier " + s.getName() + " is inactive.");
        }
        return s;
    }

    private void applyLines(PurchaseOrder po, PoRequest request) {
        Map<Long, Product> productMap = products.loadForPricing(request.items().stream().map(PoLineRequest::productId).toList());
        Set<Long> seen = new HashSet<>();
        List<PurchaseOrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PoLineRequest line : request.items()) {
            Product p = productMap.get(line.productId());
            if (p == null) {
                throw ApiException.notFound("Product", line.productId());
            }
            if (!seen.add(p.getId())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, p.getName() + " is listed twice.");
            }
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(po);
            item.setProductId(p.getId());
            item.setQuantityOrdered(QuantityRules.validate(p, line.quantity()));
            item.setExpectedUnitCost(MoneyPolicy.cost(line.unitCost()));
            items.add(item);
            total = total.add(item.getQuantityOrdered().multiply(item.getExpectedUnitCost()));
        }
        po.getItems().clear();
        po.getItems().addAll(items);
        po.setExpectedTotal(MoneyPolicy.amount(total));
        po.setExpectedDate(request.expectedDate());
        po.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
    }

    private PoDto toDto(PurchaseOrder p) {
        Map<Long, Product> productMap = products.loadForPricing(p.getItems().stream().map(PurchaseOrderItem::getProductId).toList());
        List<PoLineDto> lines = p.getItems().stream().map(i -> {
            Product pr = productMap.get(i.getProductId());
            return new PoLineDto(i.getId(), i.getProductId(), pr == null ? null : pr.getSku(), pr == null ? null : pr.getName(),
                    pr == null ? null : pr.getUnitOfMeasure().name(), i.getQuantityOrdered(), i.getQuantityReceived(), i.outstanding(),
                    i.getExpectedUnitCost(), MoneyPolicy.amount(i.getQuantityOrdered().multiply(i.getExpectedUnitCost())),
                    pr != null && pr.isTrackBatches(), pr != null && pr.isTrackExpiry());
        }).toList();
        List<Long> people = new ArrayList<>(List.of(p.getCreatedBy()));
        if (p.getApprovedBy() != null) {
            people.add(p.getApprovedBy());
        }
        Map<Long, String> names = users.displayNames(people);
        List<GrnSummaryDto> grns = p.getId() == null ? List.of() : receipts.findByPurchaseOrderIdOrderByIdAsc(p.getId()).stream()
                .map(g -> new GrnSummaryDto(g.getId(), g.getGrnNumber(), g.getReceivedAt(), g.getTotalCost(), g.getSupplierInvoiceNo()))
                .toList();
        return new PoDto(p.getId(), p.getPoNumber(), p.getSupplierId(), suppliers.require(p.getSupplierId()).getName(),
                p.getStatus(), p.getOrderDate(), p.getExpectedDate(), p.getExpectedTotal(), p.getNotes(),
                names.get(p.getCreatedBy()), p.getApprovedBy() == null ? null : names.get(p.getApprovedBy()),
                p.getCreatedAt(), p.getVersion(), lines, grns);
    }

    private GrnDto toGrnDto(GoodsReceipt g) {
        Map<Long, Product> productMap = products.loadForPricing(g.getItems().stream().map(GoodsReceiptItem::getProductId).toList());
        String poNumber = g.getPurchaseOrderId() == null ? null : orders.findById(g.getPurchaseOrderId()).map(PurchaseOrder::getPoNumber).orElse(null);
        return new GrnDto(g.getId(), g.getGrnNumber(), g.getSupplierId(), suppliers.require(g.getSupplierId()).getName(),
                g.getPurchaseOrderId(), poNumber, g.getSupplierInvoiceNo(), g.getReceivedAt(),
                users.displayNames(List.of(g.getReceivedBy())).get(g.getReceivedBy()), g.getTotalCost(), g.getNotes(),
                g.getItems().stream().map(i -> new GrnLineDto(i.getId(), i.getProductId(),
                        productMap.containsKey(i.getProductId()) ? productMap.get(i.getProductId()).getName() : null,
                        i.getQuantityReceived(), i.getUnitCost(), i.getBatchNumber(), i.getExpiryDate())).toList());
    }
}
