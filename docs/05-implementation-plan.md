# 05 — Implementation Plan and Checklist

Legend: `[x]` done · `[~]` in progress · `[ ]` pending. Every phase ends with a **gate**: the code compiles, migrations apply on a clean DB, and the phase's tests are green. The next phase does not start until the gate passes.

Each phase delivers a **vertical slice**: backend, frontend and tests together, so every feature is usable end to end as soon as it lands.

## Prerequisites (this machine, checked 2026-10-18)

| Item | Status | Action |
|---|---|---|
| JDK 17 | ✓ 17.0.12 (owner decision D15: Java 17 + Boot 3.5) | — |
| `JAVA_HOME` | ✗ value is `" C:\Program Files\Java\jdk-17"` (**leading space**, so Maven fails) | Set `JAVA_HOME` to `C:\Program Files\Java\jdk-17` (remove the leading space), then open a new terminal |
| Maven | ✓ 3.9.15 (works once JAVA_HOME is fixed) | The project will also ship `mvnw` |
| Node.js / npm | ✓ 22.15.1 / 11.4.2 | — |
| Docker | not used (owner decision D16) | — |
| Database | ✓ XAMPP MariaDB 10.4.32 (local); databases `pos_dev`, `pos_test`, user `pos_app` | The SQL is kept compatible with MySQL 8.x **and** MariaDB 10.4+ |
| Git repo | ✓ initialised at `D:\POS` (the drive root `D:\` is a separate repo owned by SYSTEM; this project no longer depends on it) | — |

## Phase 1 — Requirements, architecture, ERD, contracts, setup ✅
- [x] Architecture, module boundaries, security design — [01-architecture.md](01-architecture.md)
- [x] Core workflows and state machines — [02-business-workflows.md](02-business-workflows.md)
- [x] ER diagrams and table catalogue, migration plan — [03-database-design.md](03-database-design.md)
- [x] API conventions, endpoint catalogue, critical DTOs — [04-api-contracts.md](04-api-contracts.md)
- [x] Repository skeleton, `.gitignore`, README
- Gate: design reviewed by the project owner ✅ (2026-10-18: Java 17 + Boot 3.5, no Docker)

## Phase 2 — Backend foundation ✅
- [x] `backend/pom.xml` (Boot 3.5.16, Java 17, verified versions), Maven wrapper, `PosApplication`
- [x] Profiles: `dev`, `test`, `prod`; all secrets from env; `.env.example`
- [x] Flyway `V1`, `V2` (identity/store/reference data, permissions, roles)
- [x] `common`: BaseEntity/auditing, `MoneyPolicy`, `ErrorCode` + `ApiException` hierarchy, `GlobalExceptionHandler` (ProblemDetail), `CorrelationIdFilter`, `PageResponse`, sort whitelist, `Clock` bean, JSON logging config
- [x] Entities/repositories for V1 tables; `ddl-auto=validate`
- [x] Actuator on 8081 (liveness/readiness), OpenAPI config
- [x] Tests: MoneyPolicy rounding unit tests, exception handler tests (`@WebMvcTest`), Flyway + context smoke `@SpringBootTest` against the local `pos_test` DB (`*IT` classes, run by Failsafe)
- Gate: `mvnw verify` green; app starts against local MariaDB/MySQL; `/actuator/health/readiness` UP

## Phase 3 — Authentication, authorization, users (+ frontend shell) ✅
- [x] `V3` refresh_tokens, approval_grants
- [x] SecurityFilterChain: cookie bearer resolver, JwtDecoder/Encoder (HS256), CSRF cookie repo, CORS, 401/403 entry points as ProblemDetail
- [x] AuthService: login (lockout), refresh rotation and reuse detection, logout, `/me`; Bucket4j rate limit filter
- [x] Approval service (`/approvals`) with scoped single-use tokens
- [x] Users/roles admin APIs, `StoreAccessGuard`, audit service + `audit_logs` writes
- [x] Dev seed: DEMO store, registers, demo users (dev profile only)
- [x] **Frontend scaffold**: Next.js 16 JS app, Tailwind 4, providers, Axios instance (XSRF, refresh single-flight), login page, admin layout and permission-aware sidebar, `/pos` route shell, user management pages
- [x] Tests: auth service unit tests, security slice tests (every role × representative endpoint), refresh reuse IT, rate limit test; FE: login form validation, http refresh interceptor, nav permission tests
- Gate: login → me → refresh → logout works through the Next.js proxy

## Phase 4 — Catalog, categories, barcode lookup (backend ✅, frontend in progress)
- [x] `V4` catalog tables; tax rates seed (configurable, flagged as an example)
- [x] `BarcodeNormalizer` (GTIN check digit, UPC-A→EAN-13, UPC-E expansion)
- [x] Category CRUD (tree), Product CRUD, status, barcodes, promotions; search/filter/sort/paging with fetch joins / entity graphs (no N+1)
- [x] Demo catalogue seed (46 products, 14 categories, clearly labelled DEMO; see [demo-barcodes.md](demo-barcodes.md))
- [~] FE: category and product list/forms (RHF + Zod), filters, pagination, duplicate handling
- [x] Tests (backend): normalizer unit tests, catalog IT (duplicates, UPC-A/EAN-13 lookup, cost visibility, PRICE_MANAGE + audit, low-stock filter)
- [ ] Tests (frontend): product form validation

## Phase 5 — Inventory ledger (backend ✅, frontend in progress)
- [x] `V5` inventory tables
- [x] `InventoryService`: locked balance updates, movement writing, FEFO batch allocation, moving average cost
- [x] Adjustments and write-offs (approval threshold), stock counts and posting, low-stock and expiry queries, alerts job
- [x] Opening-balance seed for the demo products
- [~] FE: inventory list, product stock and batch detail, movement history, adjustment form, count workflow
- [x] Tests: ledger invariants (after = before + change), negative-stock rejection, FEFO allocation, average cost math, concurrent adjustment IT

## Phase 6 — POS interface, camera scanner, cart, shifts (open)
- [ ] `V6` shift, cash, held cart, void tables; shift open/current; held carts; voids
- [ ] `PricingEngine` + `/sales/quote` (shared with checkout)
- [ ] FE: full-screen POS, cart store (sessionStorage), keyboard shortcuts, search/typeahead, quantity dialog for weighed items
- [ ] Scanner: device enumeration and selection, permission/unavailable/retry states, ZXing format hints (EAN-13/8, UPC-A/E, Code 128), cooldown de-dup, track cleanup on close/unmount, unknown-barcode flow
- [ ] Tests: pricing engine rounding and allocation suite; FE scanner lifecycle with mocked `mediaDevices` and decoder, cart reducer tests, shortcut guard tests

## Phase 7 — Checkout, payments, receipts, loyalty
- [ ] `V7`, `V8`; `CheckoutService` (W4), `PaymentProvider`s (cash, manual card), idempotency service, receipt sequences
- [ ] Customers + loyalty earn/redeem in checkout
- [ ] FE: payment dialog (split tender, change due, double-submit guard), receipt preview, print CSS (80 mm / A4), reprint
- [ ] Tests: checkout success/rollback IT, **concurrent overselling IT** (N threads, last unit), **duplicate idempotency-key IT** (parallel + sequential replay), quote-mismatch, tender validation; FE checkout error handling

## Phase 8 — Returns, refunds, shift close
- [ ] `V9`; ReturnService (W7), refunds, restock rules, loyalty reversal
- [ ] Cash movements, shift close with expected cash/variance, X/Z summaries
- [ ] FE: returns screen (receipt search → item selection → approval → refund), shift close dialog, shift history
- [ ] Tests: returnable-quantity boundaries, partial→full, duplicate return, refund > paid, variance approval

## Phase 9 — Suppliers, purchase orders, receiving
- [ ] `V10`; supplier CRUD, PO lifecycle, GRN with partial delivery and batches, ad-hoc receipt
- [ ] FE: suppliers, PO editor, receiving screen
- [ ] Tests: partial/over-receipt rules, status transitions, ledger and average cost after GRN

## Phase 10 — Dashboard, reports, audit, exports
- [ ] Report queries (SQL aggregation, date-range limits), CSV streaming export
- [ ] Dashboard with defined metrics; charts (Recharts)
- [ ] Audit log viewer
- [ ] Tests: report SQL ITs against known fixtures (gross/net/refund definitions), permission tests for financial columns

## Phase 11 — Hardening
- [ ] ArchUnit module boundary tests
- [ ] Security review (OWASP ASVS L2 checklist), dependency scan, header hardening (CSP, HSTS guidance)
- [ ] Concurrency soak (k6 or JUnit parallel) on checkout
- [ ] Playwright E2E: login → open shift → scan (manual entry) → checkout cash → reprint → return
- [ ] Fix list from review

## Phase 12 — Build, run and documentation
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
