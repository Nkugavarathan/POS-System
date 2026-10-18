# 04 — API Contracts (v1)

The generated OpenAPI document (`/v3/api-docs`, UI at `/swagger-ui.html`) is the **runtime source of truth** once each phase lands. This file fixes the conventions and the contracts for the critical flows. The non-critical CRUD DTOs follow the same patterns and are finalized in their phase.

## 1. Conventions

| Item | Rule |
|---|---|
| Base path | `/api/v1` |
| Format | JSON, `camelCase`, UTF-8 |
| Money / quantity | **strings** with a fixed scale: money `"1250.00"`, quantity `"0.750"`, rate `"18.000"` |
| Time | ISO-8601 instant in UTC (`"2026-10-18T09:15:30.123Z"`); date-only `"2026-10-18"` interpreted in the store timezone |
| IDs | numeric (`long`) |
| Auth | `POS_AT` cookie (access JWT) for every request except login/refresh |
| CSRF | `X-XSRF-TOKEN` header = value of the `XSRF-TOKEN` cookie, on every POST/PUT/PATCH/DELETE |
| Correlation | `X-Correlation-Id` (optional on request, always on response) |
| Idempotency | `Idempotency-Key: <uuid v4>` **required** on: checkout, returns, goods receipts, stock adjustments, cash movements, shift close |
| Store context | The `X-Store-Id` header selects the active store for multi-store users. It is validated against `user_store_access`. If omitted, the user's default store is used. |

### Pagination
Request: `?page=0&size=20&sort=name,asc` (size ≤ 100; sort fields are whitelisted per endpoint, so unknown fields return 400).

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 134,
  "totalPages": 7,
  "sort": [{ "property": "name", "direction": "ASC" }]
}
```

### Error format (RFC 9457 Problem Details + extensions)
```json
{
  "type": "https://pos.local/problems/insufficient-stock",
  "title": "Insufficient stock",
  "status": 409,
  "detail": "Not enough stock for 1 item(s).",
  "instance": "/api/v1/sales/checkout",
  "code": "INSUFFICIENT_STOCK",
  "correlationId": "7f0c2a7e-…",
  "timestamp": "2026-10-18T09:15:30.123Z",
  "fieldErrors": [],
  "details": { "items": [{ "productId": 15, "requested": "3", "available": "1" }] }
}
```
Validation: `400 VALIDATION_FAILED` with `fieldErrors: [{ "field": "items[0].quantity", "message": "must be greater than 0", "rejectedValue": "-1" }]`.

| HTTP | Codes |
|---|---|
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `INVALID_SORT_FIELD`, `INVALID_BARCODE` |
| 401 | `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `INVALID_CREDENTIALS`, `REFRESH_TOKEN_INVALID` |
| 403 | `ACCESS_DENIED`, `ACCOUNT_LOCKED`, `ACCOUNT_DISABLED`, `APPROVAL_REQUIRED`, `APPROVAL_INVALID`, `CSRF_INVALID` |
| 404 | `RESOURCE_NOT_FOUND`, `BARCODE_NOT_FOUND` (also returned for records in stores the user cannot access) |
| 409 | `DUPLICATE_RESOURCE`, `CONCURRENT_MODIFICATION`, `INSUFFICIENT_STOCK`, `QUOTE_MISMATCH`, `SHIFT_ALREADY_OPEN`, `SHIFT_NOT_OPEN`, `INVALID_STATE_TRANSITION`, `IDEMPOTENCY_IN_PROGRESS` |
| 422 | `PRODUCT_INACTIVE`, `INSUFFICIENT_TENDER`, `PAYMENT_INVALID`, `RETURN_QUANTITY_EXCEEDED`, `RETURN_WINDOW_EXPIRED`, `REFUND_EXCEEDS_PAID`, `IDEMPOTENCY_KEY_REUSED` |
| 429 | `RATE_LIMITED` (+ `Retry-After`) |
| 500 | `INTERNAL_ERROR` (generic message, correlation id only) |

## 2. Endpoint catalogue

`Perm` = required authority (any-of when separated by `|`). "Scoped" = filtered to the user's stores.

### Auth & approvals
| Method | Path | Perm | Body → Response |
|---|---|---|---|
| GET | `/auth/csrf` | public | → 204 + `XSRF-TOKEN` cookie |
| POST | `/auth/login` | public (rate-limited) | `LoginRequest` → `AuthUserDto` + cookies |
| POST | `/auth/refresh` | refresh cookie | → `AuthUserDto` + rotated cookies |
| POST | `/auth/logout` | authenticated | → 204, cookies cleared |
| GET | `/auth/me` | authenticated | → `AuthUserDto` |
| POST | `/auth/me/password` | authenticated | `{currentPassword, newPassword}` → 204 (revokes other sessions) |
| POST | `/approvals` | the approver's credentials (rate-limited) | `ApprovalRequest` → `ApprovalGrantDto` |

### Users, roles, stores, settings
| Method | Path | Perm |
|---|---|---|
| GET | `/users` (q, role, status, page) | `USER_VIEW` |
| GET | `/users/{id}` | `USER_VIEW` |
| POST | `/users` | `USER_MANAGE` (cannot grant SUPER_ADMIN unless the caller is SUPER_ADMIN) |
| PUT | `/users/{id}` | `USER_MANAGE` |
| PATCH | `/users/{id}/status` | `USER_MANAGE` (disable → revoke tokens) |
| POST | `/users/{id}/password-reset` | `USER_MANAGE` (sets a temporary password, `mustChangePassword`) |
| PUT | `/users/{id}/pin` | `USER_MANAGE` or self |
| GET / PUT | `/roles`, `/roles/{id}/permissions` | `ROLE_MANAGE` |
| GET | `/permissions` | `ROLE_MANAGE` |
| GET / PUT | `/stores/{id}` | read: authenticated & scoped; write: `SYSTEM_CONFIG` |
| GET / POST / PUT | `/stores/{id}/registers` | read: `POS_SELL\|REGISTER_MANAGE`; write: `REGISTER_MANAGE` |
| GET / PUT | `/settings` (typed key list) | read: `SYSTEM_CONFIG\|REPORT_VIEW` (subset); write: `SYSTEM_CONFIG` |
| GET / PUT | `/tender-types`, `/tax-rates` | read: authenticated; write: `SYSTEM_CONFIG` |

### Categories & products
| Method | Path | Perm | Notes |
|---|---|---|---|
| GET | `/categories` (tree or flat, `active`) | `PRODUCT_VIEW` | |
| POST / PUT | `/categories`, `/categories/{id}` | `CATEGORY_MANAGE` | `@Version` in the body |
| PATCH | `/categories/{id}/status` | `CATEGORY_MANAGE` | Cannot deactivate a category with active products unless `force=true` |
| GET | `/products` | `PRODUCT_VIEW` | `q` (name/SKU/barcode), `categoryId`, `active`, `lowStock=true`, `brand`, `page`, `size`, `sort ∈ {name, sku, sellingPrice, stock, updatedAt}` |
| GET | `/products/{id}` | `PRODUCT_VIEW` | cost fields only with `PRODUCT_VIEW_COST` |
| GET | `/products/barcode/{barcode}` | `PRODUCT_VIEW` | normalized; 404 `BARCODE_NOT_FOUND`; returns `PosProductDto` |
| POST | `/products` | `PRODUCT_MANAGE` | 409 `DUPLICATE_RESOURCE` with `details.field = sku|barcode` |
| PUT | `/products/{id}` | `PRODUCT_MANAGE` (+ `PRICE_MANAGE` to change prices) | |
| PATCH | `/products/{id}/status` | `PRODUCT_MANAGE` | `{active, version}` |
| POST / DELETE | `/products/{id}/barcodes[/{barcodeId}]` | `PRODUCT_MANAGE` | cannot delete the last barcode |
| GET / POST / PUT | `/products/{id}/promotions` | `PRICE_MANAGE` | |

### Inventory
| Method | Path | Perm |
|---|---|---|
| GET | `/inventory` (q, categoryId, lowStock, page) | `INVENTORY_VIEW` (scoped) |
| GET | `/inventory/{productId}` (balance + batches) | `INVENTORY_VIEW` |
| GET | `/inventory/movements` (productId, type, from, to, page; range ≤ 92 days) | `INVENTORY_VIEW` |
| POST | `/inventory/adjustments` (Idempotency-Key) | `INVENTORY_ADJUST` / `INVENTORY_WRITE_OFF` (+ approval token over the threshold) |
| GET | `/inventory/adjustments[/{id}]` | `INVENTORY_VIEW` |
| POST | `/inventory/counts` · PUT `/inventory/counts/{id}/items` · POST `/{id}/submit` · POST `/{id}/post` | `INVENTORY_COUNT` / post: `INVENTORY_COUNT_POST` |
| GET | `/inventory/alerts` · POST `/inventory/alerts/{id}/acknowledge` | `INVENTORY_VIEW` |
| GET | `/inventory/expiring?days=30` | `INVENTORY_VIEW` |

### POS: shifts, cash, held carts, voids
| Method | Path | Perm |
|---|---|---|
| GET | `/pos/shifts/current` | `SHIFT_OPERATE` |
| POST | `/pos/shifts` `{registerId, openingFloat}` | `SHIFT_OPERATE` |
| POST | `/pos/shifts/{id}/cash-movements` (Idempotency-Key) | `CASH_MOVEMENT` (+ approval over the limit) |
| GET | `/pos/shifts/{id}/summary` | owner or `SHIFT_VIEW_ALL` |
| POST | `/pos/shifts/{id}/close` (Idempotency-Key) `{countedCash, denominations?, notes, approvalToken?}` | owner, or `SHIFT_VIEW_ALL` + `SHIFT_VARIANCE_APPROVE` (force) |
| GET | `/pos/shifts` (registerId, cashierId, from, to, status, page) | `SHIFT_VIEW_ALL` |
| POST / GET | `/pos/held-carts`, `/pos/held-carts?status=HELD` | `POS_HOLD_CART` |
| POST | `/pos/held-carts/{id}/resume` · `/cancel` | `POS_HOLD_CART` |
| POST | `/pos/voids` | `POS_SELL` (+ approval per rule) |

### Sales & receipts
| Method | Path | Perm |
|---|---|---|
| POST | `/sales/quote` | `POS_SELL` |
| POST | `/sales/checkout` (Idempotency-Key) | `POS_SELL` |
| GET | `/sales` (from, to, cashierId, registerId, status, tenderType, q=receipt, page) | `SALE_VIEW_ALL`, or `SALE_VIEW_OWN` (forced `cashierId=self`) |
| GET | `/sales/{id}` | as above |
| GET | `/sales/receipt/{receiptNumber}` | `SALE_VIEW_OWN\|SALE_VIEW_ALL\|RETURN_CREATE` |
| POST | `/sales/{id}/reprints` | `RECEIPT_REPRINT` → `ReceiptDto` (copy flag) |

### Returns
| Method | Path | Perm |
|---|---|---|
| POST | `/returns` (Idempotency-Key) | `RETURN_CREATE` + (`RETURN_APPROVE` or approval token) |
| GET | `/returns` (from, to, saleId, page) · `/returns/{id}` | `RETURN_CREATE\|SALE_VIEW_ALL` |

### Customers & loyalty
| Method | Path | Perm |
|---|---|---|
| GET | `/customers?q=` (name / phone / card; phone masked in lists) | `CUSTOMER_VIEW` |
| GET | `/customers/{id}` · `/customers/{id}/sales` · `/customers/{id}/loyalty` | `CUSTOMER_VIEW` |
| POST / PUT | `/customers`, `/customers/{id}` | `CUSTOMER_MANAGE` |
| GET | `/customers/loyalty/{cardNumber}` | `POS_SELL` (minimal DTO: name, masked phone, balance) |
| POST | `/customers/{id}/loyalty/adjustments` | `LOYALTY_ADJUST` |

### Suppliers & purchasing
| Method | Path | Perm |
|---|---|---|
| GET / POST / PUT / PATCH status | `/suppliers[/{id}]` | read `SUPPLIER_MANAGE\|INVENTORY_VIEW`; write `SUPPLIER_MANAGE` |
| GET | `/suppliers/{id}/purchase-history` | `SUPPLIER_MANAGE` |
| GET / POST / PUT | `/purchases/orders[/{id}]` | `PURCHASE_ORDER_MANAGE` |
| POST | `/purchases/orders/{id}/submit` · `/approve` · `/close` · `/cancel` | `PURCHASE_ORDER_MANAGE` / approve: `PURCHASE_ORDER_APPROVE` |
| POST | `/purchases/orders/{id}/receipts` (Idempotency-Key) | `INVENTORY_RECEIVE` |
| POST | `/purchases/receipts` (no PO) · GET `/purchases/receipts[/{id}]` | `INVENTORY_RECEIVE` |

### Reports, dashboard, audit
All take `from`, `to` (date, store timezone, range ≤ 366 days) and are aggregated in SQL. `format=csv` streams a CSV (needs `REPORT_EXPORT`).

| Method | Path | Perm |
|---|---|---|
| GET | `/dashboard/summary?date=` | `DASHBOARD_VIEW` |
| GET | `/reports/sales` (groupBy=day\|hour) | `REPORT_VIEW` |
| GET | `/reports/products` · `/reports/categories` | `REPORT_VIEW` (margin columns need `REPORT_FINANCIAL`) |
| GET | `/reports/payments` (reconciliation by tender, shift) | `REPORT_VIEW` |
| GET | `/reports/inventory` (valuation at average cost) | `REPORT_FINANCIAL` |
| GET | `/reports/stock-movements` | `REPORT_VIEW\|INVENTORY_VIEW` |
| GET | `/reports/returns` · `/reports/discounts` · `/reports/voids` | `REPORT_VIEW` |
| GET | `/reports/shifts` | `SHIFT_VIEW_ALL` |
| GET | `/reports/cashiers` | `REPORT_VIEW` |
| GET | `/audit-logs` (actor, action, entityType, entityId, from, to, page) | `AUDIT_VIEW` |

**Report vocabulary** (shown in the UI as tooltips):
- **Gross sales** = Σ line `gross_amount` (before any discount, tax-inclusive when prices include tax).
- **Discounts** = Σ promotion + manual + transaction discounts.
- **Refunds** = Σ `refunds.amount` in the period (by refund date).
- **Net sales** = gross − discounts − refunds.
- **Net sales excl. tax** = net sales − tax (net of refunded tax).
- **Estimated gross margin** = net sales excl. tax − cost of goods sold (average cost snapshot) + cost of restocked returns. It is *not* accounting profit.

## 3. Key DTOs

### Auth
```jsonc
// POST /auth/login
{ "username": "cashier1", "password": "••••••••" }

// AuthUserDto (login / refresh / me)
{
  "id": 7, "username": "cashier1", "fullName": "Nimal Perera",
  "roles": ["CASHIER"],
  "permissions": ["POS_SELL", "POS_HOLD_CART", "SHIFT_OPERATE", "DISCOUNT_APPLY", "..."],
  "stores": [{ "id": 1, "code": "CMB01", "name": "Colombo 01 (DEMO)" }],
  "defaultStoreId": 1,
  "maxDiscountPercent": "0.00",
  "mustChangePassword": false,
  "accessTokenExpiresAt": "2026-10-18T09:25:30Z"
}

// POST /approvals
{ "action": "DISCOUNT_OVER_LIMIT", "username": "manager1", "secret": "4321",
  "context": { "registerId": 2, "value": "15.00" } }
// → ApprovalGrantDto
{ "approvalToken": "ag_9b1f…", "action": "DISCOUNT_OVER_LIMIT",
  "approver": { "id": 3, "fullName": "S. Fernando" },
  "limitValue": "20.00", "expiresAt": "2026-10-18T09:17:30Z" }
```

### Product
```jsonc
// ProductDto (admin)
{
  "id": 15, "sku": "GRC-RICE-5KG", "name": "Samba Rice 5kg", "description": null,
  "category": { "id": 4, "name": "Rice & Grains" }, "brand": "DEMO Brand",
  "unitOfMeasure": "PACK", "fractionalQuantity": false,
  "costPrice": "1450.0000",           // only with PRODUCT_VIEW_COST
  "sellingPrice": "1890.00",
  "taxRate": { "id": 1, "code": "VAT_STD", "ratePercent": "18.000" },
  "pricesIncludeTax": true,
  "barcodes": [{ "id": 31, "barcode": "4790000000015", "type": "EAN_13", "primary": true }],
  "stock": { "storeId": 1, "quantityOnHand": "42", "reorderLevel": "10", "lowStock": false },
  "trackBatches": false, "allowPriceOverride": false, "imageUrl": null,
  "active": true, "version": 3,
  "createdAt": "…", "updatedAt": "…"
}

// ProductCreateRequest
{
  "sku": "GRC-RICE-5KG", "barcodes": ["4790000000015"], "name": "Samba Rice 5kg",
  "description": null, "categoryId": 4, "brand": "DEMO Brand", "unitOfMeasure": "PACK",
  "costPrice": "1450.0000", "sellingPrice": "1890.00", "taxRateId": 1,
  "reorderLevel": "10", "trackBatches": false, "trackExpiry": false,
  "allowPriceOverride": false, "imageUrl": null
}
// validation: sku ^[A-Z0-9][A-Z0-9-]{1,39}$ ; name 2..150 ; prices ≥ 0, scale ≤ 2 (cost ≤ 4);
// barcode: digits 8/12/13/14 with a valid check digit, or Code128 printable ASCII 1..32;
// imageUrl https only.

// PosProductDto (barcode lookup / POS search)
{ "id": 15, "sku": "…", "barcode": "4790000000015", "name": "…", "unitOfMeasure": "PACK",
  "fractionalQuantity": false, "sellingPrice": "1890.00", "taxRatePercent": "18.000",
  "activePromotion": { "type": "PERCENT", "value": "10.00", "endsAt": "…" },
  "quantityOnHand": "42", "allowPriceOverride": false }
```

### Quote & checkout
```jsonc
// POST /sales/quote   (checkout uses the same body + payments + expectedGrandTotal)
{
  "registerId": 2,
  "customerId": null,
  "loyaltyCardNumber": null,
  "items": [
    { "productId": 15, "quantity": "2" },
    { "productId": 88, "quantity": "0.750",
      "manualDiscount": { "method": "PERCENT", "value": "10.00", "reason": "Near expiry" } },
    { "productId": 91, "quantity": "1",
      "priceOverride": { "unitPrice": "450.00", "reason": "Shelf price mismatch" } }
  ],
  "transactionDiscount": { "method": "AMOUNT", "value": "100.00", "reason": "Service recovery" },
  "approvalTokens": ["ag_9b1f…"]
}

// QuoteResponse
{
  "currency": "LKR", "pricesIncludeTax": true,
  "lines": [
    { "lineNumber": 1, "productId": 15, "name": "Samba Rice 5kg", "sku": "…", "barcode": "…",
      "unitOfMeasure": "PACK", "quantity": "2", "unitPrice": "1890.00",
      "grossAmount": "3780.00", "promoDiscount": "378.00", "manualDiscount": "0.00",
      "allocatedTransactionDiscount": "36.82", "netAmount": "3365.18",
      "taxRatePercent": "18.000", "taxAmount": "513.33",
      "availableQuantity": "42", "stockSufficient": true }
  ],
  "totals": {
    "subtotal": "5120.00", "lineDiscountTotal": "466.50", "transactionDiscountTotal": "100.00",
    "discountTotal": "566.50", "taxTotal": "694.60", "grandTotal": "4553.50",
    "itemCount": 3
  },
  "loyalty": { "pointsToEarn": 45, "balance": 1200, "maxRedeemableAmount": "1200.00" },
  "requiredApprovals": [
    { "action": "PRICE_OVERRIDE", "lineNumber": 3, "reason": "Cashier cannot override prices" }
  ],
  "warnings": [{ "code": "LOW_STOCK", "productId": 88, "message": "Only 1.250 kg left" }],
  "quoteHash": "sha256:…"   // informational
}

// POST /sales/checkout   Header: Idempotency-Key: 3f1c…
{
  "...quote fields...": "…",
  "expectedGrandTotal": "4553.50",
  "payments": [
    { "tenderType": "CARD", "amount": "2000.00",
      "card": { "brand": "VISA", "last4": "4242", "authCode": "A1B2C3", "terminalReference": "T-0099" } },
    { "tenderType": "CASH", "amountTendered": "3000.00" }
  ],
  "loyaltyRedeemPoints": 0
}

// → 201 SaleDto (also returned on idempotent replay, with 200 + header Idempotent-Replayed: true)
{
  "id": 1024, "receiptNumber": "CMB01-R02-20261018-000123", "status": "COMPLETED",
  "completedAt": "…", "store": { "name": "…", "address": "…", "phone": "…", "taxRegistrationNo": "…" },
  "cashier": { "id": 7, "fullName": "Nimal Perera" }, "register": { "code": "R02" },
  "customer": null,
  "lines": [ /* same shape as quote lines, final */ ],
  "totals": { /* … */ "amountPaid": "4553.50", "amountTendered": "5000.00", "changeDue": "446.50" },
  "payments": [
    { "tenderType": "CARD", "amount": "2000.00", "cardBrand": "VISA", "cardLast4": "4242", "authCode": "A1B2C3" },
    { "tenderType": "CASH", "amount": "2553.50", "amountTendered": "3000.00", "changeGiven": "446.50" }
  ],
  "loyalty": { "pointsEarned": 45, "pointsRedeemed": 0, "balanceAfter": 1245 },
  "receiptFooter": "Thank you for shopping with us!"
}
```

**Tender rules (server):** card amount > 0 and ≤ remaining; at most one CASH line; cash `amount` = remaining after non-cash tenders; `amountTendered ≥ amount`; `changeGiven = amountTendered − amount`; Σ amounts = grandTotal exactly.

### Return
```jsonc
// POST /returns    Header: Idempotency-Key
{
  "originalReceiptNumber": "CMB01-R02-20261018-000123",
  "registerId": 2,
  "reasonCode": "CUSTOMER_CHANGED_MIND",
  "notes": null,
  "items": [{ "saleItemId": 5521, "quantity": "1", "condition": "SALEABLE", "restock": true }],
  "refunds": [{ "tenderType": "CASH", "amount": "1682.59" }],
  "approvalToken": "ag_…"
}
// → 201 ReturnDto { id, returnNumber, originalSale {id, receiptNumber}, items[{…, refundAmount, taxAmount}], refundTotal, refunds[], processedBy, approvedBy, createdAt }
// The server computes the refund; if Σ refunds ≠ computed refundTotal → 422 REFUND_AMOUNT_MISMATCH with details.expected.
```

### Shift
```jsonc
// POST /pos/shifts/{id}/close
{ "countedCash": "48250.00", "denominations": { "5000": 6, "1000": 15, "500": 6, "100": 2, "50": 1 },
  "notes": null, "approvalToken": null }
// → ShiftSummaryDto
{ "id": 77, "shiftNumber": "CMB01-R02-20261018-01", "status": "CLOSED",
  "openingFloat": "5000.00", "cashSales": "45210.00", "cashRefunds": "1682.59",
  "payIns": "0.00", "payOuts": "500.00", "cashDrops": "0.00",
  "expectedCash": "48027.41", "countedCash": "48250.00", "variance": "222.59",
  "tenderTotals": [{ "tenderType": "CARD", "amount": "31200.00", "count": 14 }],
  "salesCount": 63, "returnsCount": 1, "voidsCount": 2 }
```
