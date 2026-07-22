# Authentication JWT/JWKS Prerequisite for Catalog Administration

**Status**: Blocked pending a separate approved Authentication feature
**Owning service**: `authentication-service`
**Dependent feature**: `010-catalog-administration`

## Purpose

Feature 010 needs an authenticated Catalog Operator before a real end-to-end admin flow can be
verified through `api-gateway` and `product-service`. This file records the prerequisite boundary so
Feature 010 does not invent login, token issuance, signing keys, or runtime trust semantics.

## Current Approved Dependency

The following behavior is already approved by Feature 010:

- `authentication-service` owns login, token issuance, and JWT signing-key lifecycle.
- Remote admin requests enter through `api-gateway`.
- `api-gateway` and `product-service` act as OAuth2 resource servers that validate bearer JWTs.
- The canonical catalog-admin authority string is `CATALOG_ADMIN`.
- `product-service` derives the audited actor from its independently verified JWT security context.
- Caller-supplied actor headers are not identity sources and may be ignored or removed at the edge.
- Test-only JWTs may be used for isolated gateway/product-service security tests until the real
  authentication feature exists.

## Missing Runtime Trust Contract

Real external admin E2E is blocked until a separate approved Authentication feature defines:

- JWT issuer.
- JWT audience accepted by `api-gateway` and `product-service`.
- Signature algorithm.
- JWKS endpoint path and availability expectations.
- `kid` usage and signing-key rotation behavior.
- Required claims, including actor identity and `CATALOG_ADMIN` authority placement.
- Token lifetime, clock skew, and expiry handling.
- Revocation/logout/refresh behavior, if any.
- Distinction between invalid authentication failures and authenticated-but-forbidden failures.
- Local development strategy for obtaining a real admin token.

## Blocked E2E Scope

Until the missing contract is approved and implemented by `authentication-service`, Feature 010 may
verify:

- product-service admin behavior with Spring Security test JWTs;
- gateway route/security behavior with Spring Security test JWTs;
- isolated gateway forwarding behavior with a test upstream.

Feature 010 must not claim a complete real admin login-to-product E2E flow.

