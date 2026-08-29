# Implementation Plan: Unified API Documentation

**Branch/Feature ID**: `codex/api-documentation` / `047-api-documentation`

**Date**: 2026-08-28

**Spec**: [spec.md](spec.md)

**Status**: Implemented and verified (2026-08-29)

## Summary

Add opt-in Springdoc generation to every implemented HTTP-owning service, retain each service as the
owner of its document, and add an opt-in Gateway Swagger UI that aggregates the seven documents by
proxying only the OpenAPI JSON paths. Publish one reviewed endpoint catalog and a static validation
script. Do not change business routes, response contracts, security requirements, or cloud defaults.

## Technical Context

- **Language/version**: Java 21, PowerShell 7-compatible validation script.
- **Frameworks**: Spring Boot 3.5.16, Spring Cloud Gateway 2025.0.3, Spring Security.
- **Documentation dependency**: Springdoc OpenAPI 2.8.17, already approved and verified in Inventory
  Service. Servlet services use `springdoc-openapi-starter-webmvc-ui`; Gateway uses the matching
  WebFlux starter.
- **HTTP owners**: Authentication, Product, Campaign, Flash Sale, Inventory, Order, Payment.
- **Zero-endpoint scaffolds**: Cart and Notification.
- **Storage/messaging/cache**: No change.
- **Target environments**: local JVM and Docker Compose opt-in; cloud remains disabled.
- **Compatibility**: additive documentation only. Existing method/path/body/status/header/security
  behavior remains owned by existing controllers and filters.

## Constitution Check

### Pre-design

- **Specification traceability**: PASS — the user request is encoded in approved FR-001 through
  FR-012 and three independently testable stories.
- **Service/data ownership**: PASS — each service generates its own document; no cross-service data
  access.
- **Communication/contracts**: PASS — no HTTP behavior changes; the endpoint inventory documents
  current supported contracts before documentation wiring.
- **Durable truth/hot path**: PASS — no PostgreSQL, Redis, or Lua impact.
- **Messaging reliability**: PASS — no Kafka or outbox impact.
- **Root infrastructure ownership**: PASS — shared Compose flag and validation script remain under
  root `infra/`; service configuration stays within owning modules.
- **Observability**: PASS — Actuator and trace behavior are unchanged and excluded from totals.
- **Verification**: PASS — static catalog/config validation plus affected reactor verification.
- **ADR/exception impact**: PASS — no architecture boundary changes and no exception.

### Post-design

All checks remain PASS. The Gateway is only a local document catalog/proxy and does not own or copy
business contracts. Cloud manifests do not enable documentation, and internal business paths are
not added to Gateway routing.

## Risk Classification

- **Security**: medium, because documentation could reveal topology if exposed publicly. Mitigated
  by disabled-by-default generation and Gateway authorization that only permits documentation paths
  while the opt-in flag is true.
- **Compatibility**: low, because no business contract changes are allowed.
- **Correctness/data/financial/concurrency**: not applicable; no execution-path behavior changes.

## Endpoint Inventory Baseline

Count unique supported `HTTP method + normalized path` pairs:

| Boundary | Count | Meaning |
|---|---:|---|
| Gateway-public | 33 | Browser/mobile/admin/webhook traffic routed through API Gateway |
| Internal service-to-service | 6 | OAuth client credentials and owning-service internal APIs |
| Identity trust | 1 | Public-key-only JWKS endpoint used by JWT verifiers |
| **Total** | **40** | Excludes Actuator, Swagger, generic error, and unsupported framework routes |

The exact rows are recorded in [contracts/http-inventory.md](contracts/http-inventory.md) and
published for readers in `docs/api/README.md`.

## Architecture and Ownership

```text
Browser / developer
        |
        | API_DOCS_ENABLED=true (local explicit opt-in)
        v
API Gateway Swagger UI  /swagger-ui.html
        |
        +-- /openapi/authentication-service --> Authentication /v3/api-docs
        +-- /openapi/product-service        --> Product /v3/api-docs
        +-- /openapi/campaign-service       --> Campaign /v3/api-docs
        +-- /openapi/flashsale-service      --> Flash Sale /v3/api-docs
        +-- /openapi/inventory-service      --> Inventory /v3/api-docs
        +-- /openapi/order-service          --> Order /v3/api-docs
        +-- /openapi/payment-service        --> Payment /v3/api-docs
```

- Each service remains the source of request/response schema truth.
- Gateway supplies discovery and UI only; it does not merge specs into a new contract.
- Each proxy route matches one exact OpenAPI JSON path and rewrites it to `/v3/api-docs`.
- No `/internal/**` application route is added to Gateway.

## Dependency Plan

Add `springdoc.version=2.8.17` to the root Maven properties and reference it from:

- `services/api-gateway` with `springdoc-openapi-starter-webflux-ui`;
- `services/authentication-service`;
- `services/product-service`;
- `services/campaign-service`;
- `services/flashsale-service`;
- `services/order-service`;
- `services/payment-service`;
- `services/inventory-service` (replace its literal version with the root property).

Alternative rejected: hand-maintained standalone OpenAPI YAML for all services. It would duplicate
controller validation and DTO schemas and drift more easily. The reviewed catalog remains
hand-maintained only for ownership/access classification and framework-owned endpoint coverage.

## Configuration and Security Design

- `API_DOCS_ENABLED=false` is the single local/Compose opt-in default.
- Each service binds `springdoc.api-docs.enabled` and `springdoc.swagger-ui.enabled` to the shared
  flag, with Inventory retaining backward compatibility for `INVENTORY_API_DOCS_ENABLED`.
- Gateway binds the same flag, lists seven service documents, and denies `/swagger-ui/**`,
  `/v3/api-docs/**`, and `/openapi/**` when the flag is false.
- Servlet services may permit generated documentation paths at their private service boundary;
  no handler exists while Springdoc is disabled, and cloud ingress still passes only Gateway.
- Existing JWT, authority, ownership, internal-service subject/scope, and Stripe-signature rules
  remain on business paths.
- Cloud ConfigMaps do not set the opt-in flag.

## Contract and Compatibility Plan

- The feature adds documentation endpoints only while opted in.
- No request or response DTO is modified.
- The catalog records conditional Payment routes as implemented; live generation reflects runtime
  beans and therefore requires the corresponding Payment flags when exercising those routes.
- `POST /oauth2/token` is framework-owned and explicitly added to the Authentication OpenAPI model
  because controller scanning alone cannot discover it.
- Unsupported Spring Authorization Server endpoints are not presented as supported system APIs.

## Documentation Design

`docs/api/README.md` contains totals, per-service endpoint tables, local startup and URLs,
authorization guidance, safe-default/cloud warnings, operational endpoint explanation, and the
maintenance validation command.

## Verification Strategy

| Requirement | Static validation | Module/reactor verification | Manual local smoke |
|---|---|---|---|
| FR-001–FR-004, FR-011 | Catalog row count, uniqueness, per-service totals | N/A | Review table |
| FR-005 | Dependency/config/security checks | Compile and tests for seven services | Load direct `/v3/api-docs` |
| FR-006 | Gateway route/UI list checks | Gateway tests | Select all seven UI definitions |
| FR-007–FR-009 | Default-false and no internal route checks | Security tests | Docs unavailable by default |
| FR-010 | Documentation content checks | N/A | Follow quickstart |
| FR-012 | `verify-api-documentation.ps1` | Cross-cutting verify | N/A |

Required commands:

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\docs\verify-api-documentation.ps1
.\mvnw.cmd -pl services/api-gateway,services/authentication-service,services/product-service,services/campaign-service,services/flashsale-service,services/inventory-service,services/order-service,services/payment-service -am verify
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml -f infra/docker/compose.dev.yml config
```

Full `clean verify` is preferred if the affected-reactor command reveals cross-module failures.
Database, Kafka, load, and migration tests are omitted because no execution or data contract changes.

## Project Structure

```text
pom.xml
docs/api/README.md
infra/docker/.env.example
infra/docker/compose.yml
infra/scripts/docs/verify-api-documentation.ps1
services/api-gateway/{pom.xml,src/main/resources/application.yml,security/...}
services/{authentication,product,campaign,flashsale,order,payment}-service/
  pom.xml
  src/main/resources/application.yml
  src/main/java/**/configuration/*OpenApiConfiguration.java
  src/main/java/**/security/*SecurityConfiguration.java
services/inventory-service/{pom.xml,src/main/resources/application.yml}
specs/047-api-documentation/
```

## Migration and Rollback

- No database or message migration exists.
- Rollback is a source revert of dependencies/config/routes/docs.
- If local documentation causes a problem, set `API_DOCS_ENABLED=false`; business endpoints remain
  unchanged.

## Complexity Tracking

No constitutional exceptions. The cross-service dependency/config repetition is intentional because
each service remains independently deployable and owns its documentation runtime.

## Approval and History

- 2026-08-28 — Plan approved as implementation of the requested complete API documentation.
- 2026-08-29 — Implementation and validation completed; evidence recorded in validation.md.
