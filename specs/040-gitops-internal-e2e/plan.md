# Implementation Plan: Internal Authenticated Cloud End-to-End Smoke

**Branch**: `codex/gitops-phase22-internal-e2e` | **Date**: 2026-08-22 | **Spec**: [spec.md](spec.md)

## Summary

Add a manual PowerShell smoke runner for the canonical GitOps Phase 22 journey. The runner opens a
loopback port-forward to the Argo-managed API Gateway, authenticates an operator-provided admin and
a generated shopper, provisions Product/Campaign state through existing HTTP contracts, invokes an
Inventory-owned one-off fixture Job through the Inventory application port, submits and replays one
Flash Sale reservation, then polls the owner Order query until the `PurchaseAcceptedV1` consumer has
persisted the matching Order. No runner step reads another service database or prints credentials.

## Technical Context

**Language/Version**: PowerShell 7+ for the runner; Java 21/Spring Boot 3.x for the Inventory fixture
adapter.

**Primary Dependencies**: Existing `kubectl`, PowerShell HTTP, Kubernetes Job API, Spring Boot
application context, existing Inventory application ports, and the repository Maven wrapper. No new
production dependency.

**Storage**: Existing Inventory PostgreSQL schema is written only by Inventory's application use
case. Flash Sale, Campaign, Product, and Order databases remain service-owned and are accessed only
through their existing HTTP/Kafka boundaries.

**Testing**: Inventory unit/context tests, PowerShell parser/static checks, Kustomize client dry-run,
module Maven verification, and one live authenticated cloud smoke. No load test or Payment test.

**Target Platform**: AWS EKS `flash-sale-dev`, namespace `flash-sale`, Argo CD Application
`flash-sale-cloud`, with a local loopback Gateway port-forward.

**Project Type**: Spring Boot microservice monorepo plus root GitOps scripts.

**Performance Goals**: Bounded execution only; default whole-run deadline 600 seconds and bounded
per-call polling. Phase 22 is correctness smoke, not an SLA/load benchmark.

**Constraints**: Admin password is entered as `SecureString`; access/refresh tokens and cookies stay
in memory; no Secret or raw request payload is logged; no direct SQL/Redis/Kafka commands in the
smoke.

**Scale/Scope**: One disposable shopper, one Product Variant, one Inventory item, one Campaign, one
reservation, one replay, and one resulting Order per run.

## Constitution Check

- **Specification traceability**: All three approved decisions are recorded in `spec.md`, this plan,
  and the task ledger.
- **Service ownership**: The Inventory Job calls `InitializeInventoryUseCase`; it does not import
  JDBC/JPA or access another service database. The runner uses only Gateway HTTP for business state.
- **Communication**: External/business ingress is API Gateway; service discovery is unchanged; the
  existing `PurchaseAcceptedV1` Kafka path is observed through the Order query, not replaced.
- **Data and messaging**: PostgreSQL remains durable truth, Flash Sale's Redis Lua path is unchanged,
  and no consumer/outbox/schema behavior changes.
- **Root infrastructure ownership**: The runner and Job manifest template are under `infra/`; the
  fixture adapter remains inside `services/inventory-service`.
- **Observability**: The runner supplies one trace ID per operation and records sanitized stage
  statuses; no metrics registry or application instrumentation is added.
- **Contracts and dependencies**: Existing HTTP contracts are consumed unchanged. The fixture CLI
  contract is an operator contract; no public API route is added. No new dependency is introduced.
- **Validation**: Tests and live smoke are listed in `quickstart.md` and the task ledger.

## Architecture and Project Structure

```text
services/inventory-service/src/main/java/com/philia/flashsale/inventory/
├── adapter/in/fixture/InventoryFixtureCommandLineRunner.java
└── configuration/InventoryFixtureProperties.java

services/inventory-service/src/test/java/com/philia/flashsale/inventory/fixture/
└── InventoryFixtureCommandLineRunnerTest.java

infra/scripts/gitops/phase22-internal-e2e.ps1
specs/040-gitops-internal-e2e/
├── contracts/inventory-fixture-cli.md
├── contracts/phase22-smoke-sequence.md
├── data-model.md
├── quickstart.md
├── research.md
├── spec.md
├── plan.md
└── tasks.md
```

The Inventory fixture adapter is a driving adapter into the existing application use case. It is
enabled only by an explicit `flashsale.inventory.fixture.enabled=true` property, runs with
`SPRING_MAIN_WEB_APPLICATION_TYPE=none`, and exits after one command. Normal Deployments keep the
property disabled. The PowerShell runner renders a temporary, non-secret Job manifest using the
currently deployed Inventory image, applies it, waits for completion, and deletes the Job by default.

## Execution Design

1. **Preflight**: verify context, cloud Kustomize dry-run, Argo source/health, required Deployments,
   and anonymous Gateway admin rejection.
2. **Gateway session**: start a loopback-only port-forward; prompt for admin login/password; login;
   register/login a unique shopper; never print bearer values.
3. **Product**: create a unique draft, send a composition with a new Variant whose id is omitted so
   Product assigns ownership, read the admin detail to capture the server-assigned Variant UUID, and
   publish using ETags/versions and required trace/idempotency headers.
4. **Inventory**: render the Job with the Variant UUID/SKU/positive quantity/reason, use the current
   Inventory image and `inventory-secrets`, wait for one successful completion, and delete the Job.
5. **Campaign**: create a unique campaign window, replace its item with the Variant UUID and quantity,
   schedule with `If-Match`/`Idempotency-Key`, then activate after the schedule response/projection is
   observed. No direct Redis check is performed by the runner.
6. **Purchase**: reserve through Gateway with shopper JWT, trace, and idempotency key; require 202 and
   capture the response identities/location; replay the same request and compare identities.
7. **Order**: poll the owner reservation endpoint, then list `GET /api/v1/orders?page=0&size=100` and
   load each new candidate through `GET /api/v1/orders/{orderId}` until the detail matches purchase
   request, reservation, campaign, and variant IDs.
8. **Evidence/cleanup**: output statuses and IDs only, delete the fixture Job and temp files, and
   report inactive/archived Product/Campaign IDs for manual cleanup.

## Failure and Safety Design

- Any preflight or admin authorization failure stops before Product/Campaign mutation.
- Any Job failure reports Job phase/reason and bounded logs without Secret values.
- Any 202-without-Order timeout is a failed Phase 22 result, not a pass.
- The runner uses `try/finally` for port-forward, Job cleanup, and temporary manifest cleanup.
- No command accepts a raw password parameter; the admin password is read as a `SecureString` and
  converted only for the in-memory HTTP request body.
- Payment flags are checked and must remain disabled; the runner never calls Payment or Stripe.

## Validation Strategy

- `InventoryFixtureCommandLineRunner` unit tests cover missing/invalid properties, successful
  initialization delegation, and non-zero failure exit behavior.
- PowerShell AST/parser and static guards reject direct SQL/Redis/Kafka commands, token logging,
  non-loopback port-forward, and unbounded waits.
- `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud` remains green.
- `./mvnw.cmd -pl services/inventory-service -am verify` and `./mvnw.cmd clean verify` are required
  before live evidence.
- Live evidence is recorded in `specs/040-gitops-internal-e2e/validation.md` with command, scope,
  result, revision, and sanitized IDs.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| One opt-in Inventory fixture adapter | Current Inventory initialization transport is intentionally deferred; Phase 22 needs a service-owned way to seed a disposable variant | Direct SQL or copying the local test fixture would violate service ownership and cloud safety |
