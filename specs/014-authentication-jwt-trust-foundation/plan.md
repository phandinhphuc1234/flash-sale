# Implementation Plan: Authentication JWT Trust Foundation

**Branch**: `014-authentication-jwt-trust-foundation` | **Date**: 2026-07-25 | **Spec**: [spec.md](spec.md)

## Summary

Publish an RSA public JWKS from authentication-service and add explicit issuer/audience validation to
api-gateway and product-service. Keep the key material in deployment configuration, preserve the
existing authority converter and error contracts, and do not implement token issuance.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.5.x, Spring Security resource server/Jose, Spring MVC
**Storage**: No new durable storage
**Testing**: JUnit 5, Spring Boot tests, MockMvc/WebTestClient, Nimbus test keys
**Target Platform**: Local Compose and Kubernetes Service/DNS
**Project Type**: Three independently deployable Spring Boot services
**Performance Goals**: JWKS metadata responds within normal service latency; JWT verification remains local after JWKS cache warm-up
**Constraints**: RS256 only; no private key in source; explicit issuer/audience; fail closed on key configuration
**Scale/Scope**: One active key for MVP; `kid` retained for future overlap/rotation

## Constitution Check

- Specification and contracts are approved before production code.
- Authentication-service owns key publication; no other service database or domain model is shared.
- JWKS is a documented HTTP contract; no Kafka or cross-service database access is added.
- Service configuration and tests remain in their service modules; no shared infrastructure is moved.
- Actuator/Prometheus auto-configuration and trace conventions are unchanged.
- Unit/contract tests and affected module Maven verification are required; load tests are not applicable.
- ADR 0005 records the security/architecture boundary.

## Research Summary

See [research.md](research.md). The selected approach is RS256 + explicit JWKS + issuer/audience
validators with fail-closed public-key configuration and no token issuance in this feature.

## Architecture and Project Structure

```text
services/authentication-service/
├── pom.xml
└── src/
    ├── main/java/com/philia/flashsale/authentication/
    │   ├── adapter/in/web/JwksController.java
    │   └── configuration/JwtTrustProperties.java
    └── main/resources/application.yml

services/api-gateway/
└── src/main/java/com/philia/flashsale/gateway/security/
    └── GatewayJwtTrustConfiguration.java

services/product-service/
└── src/main/java/com/philia/flashsale/product/config/
    └── ProductJwtTrustConfiguration.java
```

The authentication JWKS controller is an inbound technical adapter. Key parsing and Spring bean
wiring stay in `configuration`; gateway/product decoder configuration stays with each service's
security boundary. No empty domain/application package is introduced.

## Configuration and Dependency Decisions

- Add `spring-security-oauth2-jose` only to authentication-service for JWK serialization; the gateway
  and product-service already have resource-server/Jose dependencies.
- Add `issuer-uri`, `jwk-set-uri`, and `audience` environment-backed properties to gateway/product.
- Read a parseable RSA public key and non-blank key id from configuration; fail startup on missing or
  malformed values.
- Add custom audience validators while retaining Spring Security's default issuer/signature/time
  validators.
- Preserve test overrides with `@ConditionalOnMissingBean` decoder configuration and test key fixtures.

## Testing Strategy

- Authentication-service unit/HTTP contract test for JWKS shape and private-field exclusion.
- Gateway and product validator tests for valid token, wrong signature, wrong issuer, wrong audience,
  expired token, and authority mapping/403 behavior.
- Affected module `verify` commands; full reactor build is optional unless cross-module compilation
  changes require it.

## Complexity Tracking

No constitution violations. The only new production dependency is justified above and is isolated to
the service that serializes the public JWK.

## Approval and History

- 2026-07-25 — Plan approved with the feature contract by the platform owner (user).
