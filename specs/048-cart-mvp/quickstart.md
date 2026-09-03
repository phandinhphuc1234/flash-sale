# Quickstart: Authenticated Cart MVP Validation

This guide defines the expected validation sequence after implementation. It does not contain
runtime secrets or replace `tasks.md`.

## Prerequisites

- Java 21, Docker Desktop, and PowerShell 7.
- Existing ignored `infra/docker/.env` with normal local platform values.
- A manually generated `CART_CLIENT_SECRET` added to ignored `infra/docker/.env` after the
  implementation updates `.env.example`.
- JWT key files and existing Authentication/Product local prerequisites remain available.

Do not paste the Cart secret into Git, terminal output, screenshots, validation evidence, or this
document. If the key is missing, the smoke runner reports only its variable name and stops.

## 1. Static artifact gate

```powershell
git diff --check
rg -n "\[NEEDS CLARIFICATION:" `
  specs/048-cart-mvp/spec.md `
  specs/048-cart-mvp/plan.md `
  specs/048-cart-mvp/research.md `
  specs/048-cart-mvp/data-model.md `
  specs/048-cart-mvp/contracts
```

Expected: no diff whitespace errors and no unresolved specification marker.

## 2. Module verification

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl "services/cart-service,services/product-service,services/authentication-service,services/api-gateway" `
  -am verify
```

Expected: all affected module, contract, security, persistence, and architecture tests pass.

## 3. Run the Cart migration separately

```powershell
docker compose --env-file infra/docker/.env `
  -f infra/docker/compose.yml `
  --profile migrations run --rm cart-migration
```

Expected: Liquibase applies the additive Cart changeset once. Re-running reports no pending change.
The long-running Cart service keeps Liquibase disabled.

## 4. Start the local topology

```powershell
docker compose --env-file infra/docker/.env `
  -f infra/docker/compose.yml `
  --profile apps up -d --build `
  postgres authentication-service product-service cart-service api-gateway
```

Inspect only status and safe logs:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml ps
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml logs --tail 100 cart-service
```

Expected: services are healthy/running and logs contain no token, secret, or Authorization value.

## 5. Run the bounded E2E and replay suite

```powershell
pwsh -NoLogo -NoProfile -File .\infra\docker\smoke\feature-048-cart.ps1 `
  -Scenario All `
  -TimeoutSeconds 900
```

The runner uses service-owned Product APIs for fixtures and public Gateway Cart APIs for behavior.
It must prove:

1. unauthenticated Cart access is 401;
2. two shoppers receive isolated Carts;
3. quantity 1 and 10 pass while 0 and 11 fail;
4. PUT create/update and 100 same-request replays produce one item;
5. Product display changes appear on the next GET;
6. remove and clear are repeatable 204 no-ops;
7. stopping Product makes GET return saved intent with `detailsAvailable=false`;
8. mutation during Product outage returns 503 and leaves Cart unchanged;
9. Product recovery restores current details;
10. the focused read profile meets the one-second p95 target.

Expected summary:

```text
FEATURE_048_MODULES=PASS
FEATURE_048_MIGRATION=PASS
FEATURE_048_SECURITY=PASS
FEATURE_048_CRUD=PASS
FEATURE_048_REPLAY=PASS
FEATURE_048_PRODUCT_DEGRADATION=PASS
FEATURE_048_PERFORMANCE=PASS
FEATURE_048_LOCAL_GATE=PASS
```

## 6. Full reactor verification

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Expected: the complete Maven reactor succeeds. Record command, scope, exit status, and CI/PR link
in the feature validation evidence selected by `tasks.md`.

## Deferred cloud handoff

Do not add Cart to EKS or run an image promotion from this feature. After Cart and Notification MVPs
both pass locally, create a reviewed deployment feature for ECR, Kustomize, service secrets,
migration Jobs, Argo reconciliation, public Gateway routes, observability, smoke, and rollback.
