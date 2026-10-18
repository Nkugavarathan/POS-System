# 05 - Implementation Plan and Checklist

Legend: `[x]` done · `[~]` in progress · `[ ]` pending. Every phase ends with a **gate**: the code compiles, migrations apply on a clean DB, and the phase's tests are green. The next phase does not start until the gate passes.

Each phase delivers a **vertical slice**: backend, frontend and tests together, so every feature is usable end to end as soon as it lands.

## Prerequisites (this machine, checked 2026-10-18)

| Item | Status | Action |
|---|---|---|
| JDK 17 | ✓ 17.0.12 (owner decision D15: Java 17 + Boot 3.5) | - |
| `JAVA_HOME` | ✗ value is `" C:\Program Files\Java\jdk-17"` (**leading space**, so Maven fails) | Set `JAVA_HOME` to `C:\Program Files\Java\jdk-17` (remove the leading space), then open a new terminal |
| Maven | ✓ 3.9.15 (works once JAVA_HOME is fixed) | The project will also ship `mvnw` |
| Node.js / npm | ✓ 22.15.1 / 11.4.2 | - |
| Docker | not used (owner decision D16) | - |
| Database | ✓ XAMPP MariaDB 10.4.32 (local); databases `pos_dev`, `pos_test`, user `pos_app` | The SQL is kept compatible with MySQL 8.x **and** MariaDB 10.4+ |
| Git repo | ✓ initialised at `D:\POS` (the drive root `D:\` is a separate repo owned by SYSTEM; this project no longer depends on it) | - |

## Phase 1 - Requirements, architecture, ERD, contracts, setup ✅
- [x] Architecture, module boundaries, security design - [01-architecture.md](01-architecture.md)
- [x] Core workflows and state machines - [02-business-workflows.md](02-business-workflows.md)
- [x] ER diagrams and table catalogue, migration plan - [03-database-design.md](03-database-design.md)
- [x] API conventions, endpoint catalogue, critical DTOs - [04-api-contracts.md](04-api-contracts.md)
- [x] Repository skeleton, `.gitignore`, README
- Gate: design reviewed by the project owner ✅ (2026-10-18: Java 17 + Boot 3.5, no Docker)

## Phase 2 - Backend foundation ✅
- [x] `backend/pom.xml` (Boot 3.5.16, Java 17, verified versions), Maven wrapper, `PosApplication`
- [x] Profiles: `dev`, `test`, `prod`; all secrets from env; `.env.example`
- [x] Flyway `V1`, `V2` (identity/store/reference data, permissions, roles)
- [x] `common`: BaseEntity/auditing, `MoneyPolicy`, `ErrorCode` + `ApiException` hierarchy, `GlobalExceptionHandler` (ProblemDetail), `CorrelationIdFilter`, `PageResponse`, sort whitelist, `Clock` bean, JSON logging config
- [x] Entities/repositories for V1 tables; `ddl-auto=validate`
- [x] Actuator on 8081 (liveness/readiness), OpenAPI config
- [x] Tests: MoneyPolicy rounding unit tests, exception handler tests (`@WebMvcTest`), Flyway + context smoke `@SpringBootTest` against the local `pos_test` DB (`*IT` classes, run by Failsafe)
- Gate: `mvnw verify` green; app starts against local MariaDB/MySQL; `/actuator/health/readiness` UP

## Phase 3 - Authentication, authorization, users (+ frontend shell) ✅
- [x] `V3` refresh_tokens, approval_grants
- [x] SecurityFilterChain: cookie bearer resolver, JwtDecoder/Encoder (HS256), CSRF cookie repo, CORS, 401/403 entry points as ProblemDetail
- [x] AuthService: login (lockout), refresh rotation and reuse detection, logout, `/me`; Bucket4j rate limit filter
- [x] Approval service (`/approvals`) with scoped single-use tokens
- [x] Users/roles admin APIs, `StoreAccessGuard`, audit service + `audit_logs` writes
- [x] Dev seed: stores, registers, staff accounts (dev profile only)
- [x] **Frontend scaffold**: Next.js 16 JS app, Tailwind 4, providers, Axios instance (XSRF, refresh single-flight), login page, admin layout and permission-aware sidebar, `/pos` route shell, user management pages
- [x] Tests: auth service unit tests, security slice tests (every role × representative endpoint), refresh reuse IT, rate limit test; FE: login form validation, http refresh interceptor, nav permission tests
- Gate: login → me → refresh → logout works through the Next.js proxy

## Phase 4 - Catalog, categories, barcode lookup ✅
- [x] `V4` catalog tables; tax rates seed (configurable, flagged as an example)
- [x] `BarcodeNormalizer` (GTIN check digit, UPC-A→EAN-13, UPC-E expansion)
- [x] Category CRUD (tree), Product CRUD, status, barcodes, promotions; search/filter/sort/paging (no N+1)
- [x] Starter catalogue seed (46 products, 14 categories; see [barcodes.md](barcodes.md))
- [x] FE: products list/editor (barcode prefill), categories tree
- [x] Tests (backend): normalizer unit tests, catalog IT
- [ ] Tests (frontend): product form validation

## Phase 5 - Inventory ledger ✅
- [x] `V5` inventory tables; `InventoryService` (locked balances, FEFO, moving average cost)
- [x] Adjustments/write-offs with approval, stock counts, low-stock and expiry queries
- [x] FE: stock list, product ledger, adjustments, counts
- [x] Tests: ledger invariants, negative-stock rejection, FEFO, average cost, concurrency

## Phase 6 - POS interface, camera scanner, cart, shifts ✅
- [x] `V6` shifts, cash movements, held carts, voids; `PricingEngine` + `/sales/quote`
- [x] FE: full-screen POS, keyboard shortcuts, camera scanner dialog, held carts, cash in/out, shift close
- [ ] Tests (frontend): scanner lifecycle with mocked `mediaDevices`, cart store

## Phase 7 - Checkout, payments, receipts, loyalty ✅
- [x] `V7`, `V8`; idempotent transactional checkout; cash / manual card (last 4 only) / loyalty tenders
- [x] FE: payment dialog (split tender, change due, double-submit guard), receipt (80 mm / A4), reprint
- [x] Tests: CheckoutIT (replay, split tender, quote mismatch, stock rollback, discount approval, two-till race, shift variance)

## Phase 8 - Returns, refunds, shift close ✅
- [x] `V9`; ReturnService (window, approval, tender limits, restock/write-off, loyalty reversal)
- [x] FE: returns list, new-return wizard (receipt → items → preview → refund → approval), return detail; shift history and Z-report
- [ ] Tests: dedicated ReturnIT (boundaries, partial→full, duplicate return)

## Phase 9 - Suppliers, purchase orders, receiving ✅
- [x] `V10`; suppliers, PO lifecycle, GRN with partial delivery, batches and expiry
- [x] FE: suppliers, PO editor, PO detail with receiving
- [ ] Tests: dedicated PurchaseIT (partial/over-receipt, transitions, average cost after GRN)

## Phase 10 - Dashboard, reports, audit, exports ✅
- [x] Report queries (store-local days, 366-day limit), CSV export with formula-injection guard, export audited
- [x] Dashboard (KPIs vs yesterday same time, 14-day chart, hourly, top products, operations tiles)
- [x] Reports page (sales, products, stock, valuation for cost-permitted roles), audit log viewer
- [x] Tests: ReportsIT (dashboard/sales totals, CSV, permission denials, audit search), CsvWriterTest

## Phase 10b - Online shop (click & collect) ✅
- [x] `V11`: online orders, items, events, customer accounts/notifications, outbox, store-scoped rescue promotions
- [x] Customer auth (separate SHOP_* cookies and token type), loyalty card linking (card + phone)
- [x] Rescue deals: near-expiry batches marked down automatically per store (also at the till)
- [x] Live packing tracker, substitution approval ("never pay more"), "I'm here" check-in, buy-again predictions
- [x] Staff: packing board, packing screen (scan to pick), POS online pickup (F7) paying the packed total
- [x] Notifications: in-app inbox + email/SMS outbox (logging sender in dev; a real provider is not wired)
- [x] FE storefront under `/shop`: home, browse, product, rescue, cart, checkout with pickup windows, sign-in/register, orders, tracker, notifications, account
- [x] Tests: ShopFlowIT (substitution → ready → collect as one sale, stock picked once; cancel restores stock; token separation; rescue per store)

## Phase 11 - Hardening
- [ ] ArchUnit module boundary tests
- [ ] Security review (OWASP ASVS L2 checklist), dependency scan, header hardening (CSP, HSTS guidance)
- [ ] Concurrency soak (k6 or JUnit parallel) on checkout
- [ ] Playwright E2E: login → open shift → scan (manual entry) → checkout cash → reprint → return
- [ ] Fix list from review

## Phase 12 - Build, run and documentation
- [ ] Production build and run scripts (backend jar, `next build` / `next start`); Docker deliverables dropped (D16)
- [ ] `docs/setup.md`, `docs/operations.md` (backup/restore, migrations, HTTPS/reverse proxy, CORS, secrets rotation), `docs/troubleshooting.md` (camera permissions, HTTPS requirement, CSRF errors, clock skew, Flyway checksum)
- [ ] Final acceptance run against the deliverables list

## Backlog (designed, not scheduled)
- Integrated card gateway (W4b intent/confirm flow)
- Inter-store transfers (`TRANSFER_OUT`/`TRANSFER_IN` reserved)
- Price-embedded variable-weight barcodes (prefix 20–29)
- Offline POS mode
- PDF report export
- Transactional outbox for external integrations
- Shared rate-limit store and ShedLock for multi-instance deployment

## Key risks
| Risk | Mitigation |
|---|---|
| Boot 3.5 is the last 3.x line, so free support ends eventually | Keep framework code idiomatic; plan a Boot 4 / Java 21 upgrade after v1 |
| No Docker → no Testcontainers | Integration tests run against a dedicated local `pos_test` schema (Flyway `clean` before each run, enabled only in the test profile) |
| MariaDB locally vs MySQL in production | Only portable SQL in migrations (no `utf8mb4_0900_*` collation, LONGTEXT instead of JSON columns); the dialect is set by config |
| Camera scanning needs a secure context | `localhost` works in dev; production needs HTTPS (documented) |
| Tax configuration is a legal matter | Rates are config with an "example" flag; an accountant signs off before go-live |
