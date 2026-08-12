# Quickstart: Product Catalog Administration

This guide describes how to validate Feature 010 after implementation. It is not an implementation
script and does not replace tasks or automated tests.

## Prerequisites

- Java 21.
- Docker available for Testcontainers PostgreSQL.
- Active feature pointer remains `specs/010-catalog-administration`.
- Authentication/JWT foundation is not yet implemented for real external admin E2E. Until the
  separate Authentication feature approves and implements the JWT/JWKS runtime trust contract,
  automated checks use approved test-only JWT support that still exercises `CATALOG_ADMIN`
  enforcement.

## Required Validation Commands

From repository root:

```powershell
.\mvnw.cmd -pl services/product-service -am verify
.\mvnw.cmd -pl services/api-gateway -am verify
```

Run the full reactor only if implementation touches shared modules or root Maven configuration:

```powershell
.\mvnw.cmd clean verify
```

## Manual Local Flow

Real manual login-to-admin E2E is blocked until `authentication-service` can issue a signed JWT with
the approved `CATALOG_ADMIN` authority and expose the approved JWKS runtime contract. The scenarios
below describe the target flow once that prerequisite exists; before then, rely on the automated
gateway/product-service security tests and isolated forwarding tests.

Start local backing services if the implementation quickstart requires a real database:

```powershell
docker compose -f infra/docker/compose.dev.yml up -d postgres
```

Start product-service and api-gateway using the repository's normal Spring Boot commands or IDE run
configurations. The exact command may differ after tasks define local profiles.

## Scenario 1: Create Draft and Verify Public Hidden

1. Send `POST /api/v1/admin/catalog/products` through api-gateway with:
   - `Authorization: Bearer <CATALOG_ADMIN token>`
   - `Idempotency-Key: create-product-001`
   - `X-Trace-Id: quickstart-create-001`
2. Expect `201` with `status = DRAFT` and `version = 0`.
3. Send `GET /api/v1/admin/catalog/products/{productId}`.
4. Expect admin detail to include the draft.
5. Send `GET /api/v1/catalog/products/{slug}`.
6. Expect shopper `PRODUCT_NOT_FOUND`.

## Scenario 2: Idempotency Replay

1. Repeat the exact create request with the same actor, idempotency key, and body within 7 days.
2. Expect the original stored response, not a duplicate Product.
3. Repeat the same key with a changed request body.
4. Expect `409 IDEMPOTENCY_KEY_REUSED`.

## Scenario 3: Maintain Composition Atomically

1. Send `PUT /api/v1/admin/catalog/products/{productId}/composition` with `If-Match: <current version>`.
2. Include Product content, one active Variant with VND base price, existing Category membership,
   and media URL metadata.
3. Expect `200` with incremented Product version.
4. Repeat with one invalid Category ID.
5. Expect rejection and no partial mutation persisted.

## Scenario 4: Lifecycle and Shopper Visibility

1. Publish with `POST /api/v1/admin/catalog/products/{productId}/publish`, `If-Match`, and
   `Idempotency-Key`.
2. Expect `ACTIVE`, `publishedAt`, and incremented version.
3. Confirm shopper detail now appears if Feature 009 visibility rule is satisfied.
4. Deactivate with expected version and idempotency key.
5. Confirm shopper detail is hidden.
6. Reactivate with expected version and idempotency key.
7. Archive with expected version and idempotency key.
8. Confirm admin detail still works and shopper detail is hidden.
9. Attempt another mutation after archive.
10. Expect `409 ARCHIVED_PRODUCT_IMMUTABLE`.

## Scenario 5: Optimistic Lock Conflict

1. Read the same Product detail twice and capture the same version.
2. Apply one valid composition or lifecycle command with that version.
3. Apply a second command using the stale original version.
4. Expect `409 STALE_PRODUCT_VERSION` and no second mutation.

## Scenario 6: Audit Evidence

1. Perform one successful mutation and one rejected mutation.
2. Verify product-service stores or exposes test-verifiable audit evidence containing:
   - actor ID;
   - trace ID;
   - command name;
   - target Product ID when available;
   - outcome;
   - error code for rejected/conflict outcomes.

## Out of Scope Checks

Feature 010 should not require:

- Kafka broker;
- outbox table;
- Redis;
- binary media storage;
- Category hierarchy CRUD;
- stock, campaign, cart, order, or payment services.
