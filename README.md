# Supermarket POS

A supermarket point-of-sale and back-office system: cashier checkout with laptop-camera barcode scanning, a per-store inventory ledger, purchasing, returns, shifts and reporting.

This is an independent implementation. It uses no proprietary code, branding or assets from any retailer. All seed data is fictional and labelled **DEMO**.

> **Status:** phases 1–10 and the online shop are built and covered by integration tests (see the [implementation plan](docs/05-implementation-plan.md)). Hardening (Phase 11) and production deployment docs (Phase 12) are still open. **This project is not production-ready.** That needs the security review, a real email/SMS provider, HTTPS deployment and payment-terminal verification listed in the plan.

## What's inside

- **Back office** (`/login` → `/dashboard`): dashboard, reports and CSV export, products, categories, inventory ledger, rescue deals, online-order packing board, sales and receipts, returns, shifts, customers and loyalty, suppliers, purchase orders and receiving, users and roles, settings, audit log.
- **POS** (`/pos`): full-screen till with camera or USB barcode scanning, split tender, holds, voids, approvals, receipts, and online order pickup (F7).
- **Online shop** (`/shop`, also the site root): click and collect, with a few things most shops don't have:
  - **Rescue deals:** near-expiry batches are marked down automatically, for that store only, online and at the till.
  - **Live packing tracker:** follow the order item by item.
  - **Substitution approval:** the customer accepts or refuses a swap and never pays more.
  - **"I'm here" check-in:** shows up on the board and at the till.
  - **One loyalty card** across the till and the app, plus "Running low?" reminders.

## Stack

| Part | Technology |
|---|---|
| `backend/` | Java 17, Spring Boot 3.5 (Web, Security, Data JPA, Validation, Actuator), Flyway, MariaDB 10.4 locally / MySQL 8 compatible SQL, springdoc-openapi, JUnit 5 |
| `frontend/` | Next.js 16 (App Router, JavaScript), React 19, Tailwind CSS 4, TanStack Query, Zustand, Axios, React Hook Form + Zod, Recharts, @zxing/browser, Vitest |

## Run locally

1. Start MariaDB/MySQL (e.g. XAMPP) and create the `pos_dev` and `pos_test` databases with a dedicated user.
2. Copy `backend/.env.example` to `backend/.env` and fill in `DB_URL`, `TEST_DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`.
3. Backend: `cd backend && set -a && . ./.env && set +a && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (API on :8080; Flyway migrates and loads the DEMO seed).
4. Frontend: `cd frontend && npm install && npm run dev` → http://localhost:3000 (shop) and http://localhost:3000/login (staff).
5. Tests: `cd backend && ./mvnw verify` (needs `TEST_DB_URL`; the test schema is wiped) and `cd frontend && npm test`.

Theme colours live in `frontend/src/app/globals.css` (`--primary`, `--secondary`); every screen uses `bg-primary`, `text-secondary` and so on.

## Notes

- **Camera scanning** needs a secure context: `http://localhost` during development, HTTPS in production. A USB barcode scanner also works because it types into the POS search field. You can type barcodes by hand to test without any scanner.
- **Card payments** are recorded after an external terminal approves them. The system does not charge cards itself.
- **Credentials:** demo accounts exist only in the `dev` profile seed. Never deploy with them.
