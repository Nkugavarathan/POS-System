# Supermarket POS

A supermarket point-of-sale and back-office system: cashier checkout with laptop-camera barcode scanning, a per-store inventory ledger, purchasing, returns, shifts and reporting.

This is an independent implementation. It uses no proprietary code, branding or assets from any retailer. All seed data is fictional and labelled **DEMO**.

> **Status: Phase 1 (design).** No runnable code yet. See the [implementation plan](docs/05-implementation-plan.md) for progress. This project is not production-ready. That claim requires the security, deployment and payment-integration verification described in the plan.

## Stack

| Part | Technology |
|---|---|
| `backend/` | Java 17, Spring Boot 3.5 (Web, Security, Data JPA, Validation, Actuator), Flyway, MySQL 8.4, springdoc-openapi, JUnit 5, Mockito, Testcontainers |
| `frontend/` | Next.js 16 (App Router, JavaScript), React 19, Tailwind CSS 4, TanStack Query, Zustand, Axios, React Hook Form + Zod, Recharts, @zxing/browser, Vitest, Playwright |
| `infrastructure/` | Docker Compose (MySQL, backend, frontend), environment templates |

## Repository layout

```
.
├── backend/          Spring Boot modular monolith
├── frontend/         Next.js application (admin + full-screen POS)
├── infrastructure/   docker-compose, env examples
└── docs/
    ├── 01-architecture.md
    ├── 02-business-workflows.md
    ├── 03-database-design.md      (ER diagrams, table catalogue)
    ├── 04-api-contracts.md
    └── 05-implementation-plan.md  (phase checklist, prerequisites)
```

## Prerequisites

- JDK 17 with `JAVA_HOME` pointing to it
- Node.js 22 LTS
- Docker Desktop (runs MySQL and the integration tests)

Setup and run commands are added as each phase lands.

## Notes

- **Camera scanning** needs a secure context: `http://localhost` during development, HTTPS in production. A USB barcode scanner also works because it types into the POS search field. You can type barcodes by hand to test without any scanner.
- **Card payments** are recorded after an external terminal approves them. The system does not charge cards itself.
- **Credentials:** demo accounts exist only in the `dev` profile seed. Never deploy with them.
