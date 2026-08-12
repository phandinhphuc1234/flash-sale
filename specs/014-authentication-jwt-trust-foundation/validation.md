# Validation Evidence: Authentication JWT Trust Foundation

## Contract and module verification

| Scope | Command | Result | Exit |
|---|---|---|---:|
| authentication-service | `./mvnw -pl services/authentication-service -am verify` | 2 tests passed and jar repackaged | 0 |
| api-gateway | `./mvnw -pl services/api-gateway -am clean test` | Gateway suite passed after clean rebuild | 0 |
| product-service | `./mvnw -pl services/product-service -am clean test` | 25 tests passed, including Testcontainers PostgreSQL | 0 |
| Compose application profile | `docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml --profile apps config` | Rendered issuer/audience/JWKS/public-key variables | 0 |

## Covered behaviors

- JWKS returns only the configured public RSA key with `RS256`, `sig`, and `kid`.
- Missing public-key configuration fails authentication-service startup; no ephemeral production key is
  generated.
- Gateway and product audience validators accept `flash-sale-api` and reject another audience.
- Existing test-only decoder overrides remain isolated from the production decoder bean.
- Existing Gateway 401/403, route forwarding, Product admin, and Actuator regressions remain green.
- Focused post-change validator tests pass: Gateway 3 tests and Product 3 tests.

## Deferred by approved scope

Login/token issuance, refresh/logout, revocation, custom lifetime/clock-skew policy, automated key
rotation, and real login-to-product E2E remain intentionally deferred to a separate approved feature.

## Read-only Spec Kit consistency check

On 2026-07-25, the Feature 014 artifacts were checked with the Spec Kit prerequisite command and a
read-only cross-artifact review. The review found 10 functional requirements, 4 success criteria, and
13 dependency-ordered tasks; every requirement has a corresponding task or contract/evidence entry,
there are no unresolved clarification markers, and no constitution conflict was found. The only
intentionally deferred behaviors (issuance, refresh, revocation, custom expiry, and rotation) are
explicitly excluded in `spec.md`, `plan.md`, and the JWKS contract.
