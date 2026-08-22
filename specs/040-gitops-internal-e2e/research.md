# Research: Internal Authenticated Cloud End-to-End Smoke

## Decision 1 — Use an existing admin account at runtime

**Decision**: The runner accepts an admin login and reads the password with `Read-Host -AsSecureString`.

**Rationale**: Authentication registration intentionally strips privilege fields and always creates
`ROLE_USER`; there is no safe public role-promotion endpoint. Reusing an existing `ROLE_ADMIN` keeps
the smoke within Authentication's ownership boundary and avoids credentials in `.env`, GitHub, or
Kubernetes.

**Alternatives rejected**: SQL elevation in `auth_db` (cross-boundary and unsafe); a new public admin
bootstrap endpoint (larger security feature, not required for the smoke).

## Decision 2 — Inventory-owned fixture Job/CLI

**Decision**: Add an opt-in Spring Boot command-line adapter to `inventory-service`. A temporary
Kubernetes Job runs the already deployed Inventory image with `flashsale.inventory.fixture.enabled`
and variant/SKU/quantity/reason properties, calls `InitializeInventoryUseCase`, prints only bounded
status/identity evidence, and exits.

**Rationale**: Inventory initialization is explicitly transport-deferred in Feature 016. The existing
local fixture calls the application use case but also deletes rows with `JdbcTemplate`; that cleanup
and direct database access are not suitable for cloud. A service-owned one-shot adapter preserves the
application boundary and can be disabled in normal Deployments.

**Alternatives rejected**: public initialization HTTP route (new production contract/ADR); direct
SQL or `kubectl exec` (violates service ownership); pre-seeded IDs (does not prove the admin path).

## Decision 3 — Verify Order by owner query

**Decision**: Poll `GET /api/v1/orders?page=0&size=100` with the shopper JWT and match the new Order's
`purchaseRequestId`, `reservationId`, `campaignId`, and `variantId`.

**Rationale**: Order is created only after `PurchaseAcceptedV1` is consumed; there is no Order POST.
The owner query is the supported public evidence and avoids reading `order_db` or Kafka directly.

## Decision 4 — Keep Payment disabled

**Decision**: Assert the seven Payment flags remain disabled and stop the flow at the initial
`PENDING_PAYMENT` Order state.

**Rationale**: Stripe enablement is canonical roadmap 24 and is explicitly out of Phase 22.

## Decision 5 — Loopback Gateway port-forward

**Decision**: Use the existing Phase 18 pattern with `kubectl port-forward --address 127.0.0.1` and
always terminate it in `finally`.

**Rationale**: The Gateway remains ClusterIP; no public exposure is authorized by Phase 22.
