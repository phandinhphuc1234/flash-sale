# Authentication JWT/JWKS Prerequisite for Catalog Administration

**Status**: Trust foundation approved by Feature 014; token issuance remains a separate feature
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

## Approved Runtime Trust Contract (Feature 014)

Feature 014 (`specs/014-authentication-jwt-trust-foundation/`) approves the following runtime trust
values:

- RS256 signing algorithm.
- Issuer `http://authentication-service:8080`.
- Audience `flash-sale-api`.
- JWKS endpoint `GET /.well-known/jwks.json`.
- Required `kid`, `sub`, `iss`, `aud`, `iat`, and `exp` claims; `authorities` carries
  `CATALOG_ADMIN` when the caller is a catalog administrator.
- Invalid signature/issuer/audience/expiry is 401; a valid token without the route authority is 403.
- Private keys are deployment secrets and missing/malformed key configuration fails closed.

## Deferred Authentication Decisions

Real external admin E2E is blocked until a separate approved Authentication feature defines:

- Token issuance and credential/login behavior.
- Refresh/logout/revocation and custom lifetime/clock-skew policy.
- Automated signing-key rotation and overlapping-key deployment procedure.
- Local strategy for obtaining a real admin token (until a separate issuance feature is approved).

## Blocked E2E Scope

Until a separate token-issuance feature is approved and implemented by `authentication-service`, Feature 010 may
verify:

- product-service admin behavior with Spring Security test JWTs;
- gateway route/security behavior with Spring Security test JWTs;
- isolated gateway forwarding behavior with a test upstream.

Feature 010 must not claim a complete real admin login-to-product E2E flow.
