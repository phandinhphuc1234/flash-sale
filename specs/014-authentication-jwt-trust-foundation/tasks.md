# Tasks: Authentication JWT Trust Foundation

**Input**: Design documents from `/specs/014-authentication-jwt-trust-foundation/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

## Phase 1: Setup

- [X] T001 Record ADR 0005 and mark Feature 010's JWT/JWKS prerequisite as completed in its contract
  and dependency links (`docs/adr/0005-authentication-jwt-trust-foundation.md`,
  `specs/010-catalog-administration/contracts/authentication-jwt-jwks-prerequisite.md`)
- [X] T002 [P] Add the approved issuer, audience, JWKS URI, key id, and public-key configuration
  placeholders to authentication/gateway/product runtime configuration (`services/authentication-service/src/main/resources/application.yml`,
  `services/api-gateway/src/main/resources/application.yml`, `services/product-service/src/main/resources/application.yml`)
- [X] T003 [P] Add the approved JOSE dependency to authentication-service and document the dependency
  rationale in `services/authentication-service/pom.xml`

## Phase 2: Foundational trust contract

- [X] T004 [P] Add JWKS HTTP contract tests for status, content type, RSA fields, `kid`, and private-key
  exclusion in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/adapter/in/web/JwksControllerTests.java`
- [X] T005 [P] Add resource-server validator tests for subject and audience semantics, with Spring's
  standard issuer/time validation retained for the configured decoder, and
  authority semantics in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/security/GatewayJwtTrustConfigurationTests.java` and `services/product-service/src/test/java/com/philia/flashsale/product/config/ProductJwtTrustConfigurationTests.java`
- [X] T006 Implement fail-closed RSA public-key properties, key parsing, and JWKS serialization in
  `services/authentication-service/src/main/java/com/philia/flashsale/authentication/configuration/JwtTrustProperties.java`, `services/authentication-service/src/main/java/com/philia/flashsale/authentication/configuration/JwtPublicKeyConfiguration.java`, and `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/in/web/JwksController.java`
- [X] T007 Implement explicit issuer/audience validator beans while preserving test decoder overrides
  in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/security/GatewayJwtTrustConfiguration.java` and `services/product-service/src/main/java/com/philia/flashsale/product/config/ProductJwtTrustConfiguration.java`

## Phase 3: User Story 1 - Publish verifiable signing key (P1)

- [X] T008 [US1] Verify the JWKS response and fail-closed startup behavior with the authentication-service
  focused Maven verification command and record evidence in `specs/014-authentication-jwt-trust-foundation/validation.md`

## Phase 4: User Story 2 - Validate canonical token contract (P1)

- [X] T009 [US2] Verify gateway valid/invalid token handling and existing 401/403 contracts with
  `./mvnw -pl services/api-gateway -am verify` and record evidence in `specs/014-authentication-jwt-trust-foundation/validation.md`
- [X] T010 [US2] Verify product-service valid/invalid token handling and authority mapping with
  `./mvnw -pl services/product-service -am verify` and record evidence in `specs/014-authentication-jwt-trust-foundation/validation.md`

## Phase 5: Polish and governance

- [X] T011 [P] Update local secret/JWKS setup guidance without committing private material in
  `infra/docker/.env.example`, `infra/docker/README.md`, and `services/authentication-service/README.md`
- [X] T012 Run the feature quickstart and append command, result, exit status, and remaining deferred
  scope to `specs/014-authentication-jwt-trust-foundation/validation.md`
- [X] T013 Run `speckit-analyze` against this feature's spec, plan, and tasks; resolve only approved
  artifact issues before marking the feature Verified

## Dependencies and Execution Order

```text
T001-T003 -> T004-T007 -> T008 -> T009-T010 -> T011-T013
```

T004-T005 are test-first and may run in parallel. T006-T007 implement only after the contract tests
are present. Login, refresh, revocation, and key-rotation automation are not tasks in this feature.

## Implementation Strategy

1. Establish the approved contract and dependency/configuration seams.
2. Write red contract/validator tests, then implement the minimal trust foundation.
3. Verify each affected service independently, document local secret setup, and preserve deferred
   token-issuance scope for a later approved feature.

## Approval and History

- 2026-07-25 — Task graph approved by the platform owner (user) with the Authentication trust contract.
- 2026-07-25 — All tasks verified with module tests, Compose rendering, and read-only artifact analysis.
