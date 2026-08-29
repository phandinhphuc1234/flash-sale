# Validation: Unified API Documentation

Feature branch: `codex/api-documentation`

## Static catalog and safety checks

Command:

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\docs\verify-api-documentation.ps1
```

Result: PASS

```text
API_DOCUMENTATION=PASS (40 supported endpoints; 7 service documents; defaults disabled)
```

The verifier confirmed 40 unique method/path pairs, totals of 33 Gateway-public, 6 Internal, and
1 Identity trust endpoint, owner totals, seven Springdoc service dependencies, seven Gateway
document proxies, local-only opt-in defaults, and no cloud `API_DOCS_ENABLED=true` setting.

## Compile and test

Command:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl services/api-gateway,services/authentication-service,services/product-service,services/campaign-service,services/flashsale-service,services/inventory-service,services/order-service,services/payment-service `
  -am -DskipTests compile
```

Result: PASS — all selected modules and their reactor dependencies compiled successfully.

Command:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/api-gateway -am verify
```

Result: PASS — Gateway and `common-web` verification completed; 196 Gateway tests passed with
zero failures, errors, or skips.

## Compose configuration

Command:

```powershell
docker compose --env-file infra/docker/.env.example `
  -f infra/docker/compose.yml --profile apps config --quiet
```

Result: PASS — Compose rendered successfully and retained `API_DOCS_ENABLED=false` by default.

## Diff hygiene and scope

Command:

```powershell
git diff --check
```

Result: PASS.

The implementation is additive documentation/runtime wiring only. No business controller mapping,
response envelope, authorization rule, database schema, Kafka contract, or cloud Secret was changed.
