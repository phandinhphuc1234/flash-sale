# Inventory Service Plan Validation Guide

This guide defines the evidence expected after implementation. It intentionally contains no migration
SQL or production implementation code.

## Prerequisites

- Java 21 and the repository Maven wrapper.
- Docker Desktop for PostgreSQL/Kafka/Testcontainers scenarios.
- An approved `infra/docker/.env` with non-default secrets.
- Approved initialization, HTTP, Kafka, authorization, and outbox decisions.

Initialization transport is intentionally not selected yet. During this core phase, seed a variant in
an integration fixture or call the application use case directly; no public initialization endpoint is
exposed until the deferred Kafka/HTTP decision is approved.

## Module verification

```powershell
.\mvnw.cmd -pl services/inventory-service -am verify
```

Expected: context, domain, persistence, contract, and integration tests pass.

## Required scenarios

1. Run Liquibase forward migration and verify all four tables, checks, indexes, and unique constraints.
2. Initialize one variant twice and verify one inventory item.
3. Increase stock, then reject a decrease below allocated quantity without partial state.
4. Allocate concurrently (80 + 80 against 100 available) and verify one success only.
5. Retry a successful command with the same request ID and verify no duplicate movement/allocation.
6. Release an active allocation and verify on-hand is unchanged.
7. Reconcile 80 sold + 20 returned from 100 and verify balances and terminal status.
8. Force a transaction failure and verify inventory, movement, allocation, and outbox rows all roll back.
9. Publish an outbox row, simulate duplicate delivery, and verify idempotent downstream handling.
10. Verify Gateway `INVENTORY_ADMIN` enforcement, service-side revalidation, health, readiness,
    Prometheus endpoint, trace ID propagation, and bounded error logs.

## Evidence

Record command, scope, result/exit status, and CI/PR reference in the feature tasks or validation
evidence document. A checked task alone is not test evidence.
