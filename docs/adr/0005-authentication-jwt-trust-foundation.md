# ADR 0005: Authentication JWT Trust Foundation

**Status**: Accepted
**Date**: 2026-07-25

## Context

Gateway and product-service currently use isolated test JWT decoders. Real cross-service requests
need one verifiable trust contract without sharing private signing material or inventing login
behavior inside another feature.

## Decision

- authentication-service owns JWT signing-key publication and exposes `GET /.well-known/jwks.json`.
- The contract uses RS256, a required `kid`, issuer `http://authentication-service:8080`, audience
  `flash-sale-api`, and the canonical `CATALOG_ADMIN` authority.
- gateway and product-service validate signature, issuer, audience, and standard time claims locally.
- Missing/malformed key configuration fails closed. Private keys are deployment secrets only.
- Login, token issuance, refresh, logout, revocation, and automated rotation remain separate approved
  work; this feature only establishes trust material and verification.

## Alternatives considered

- HS256 was rejected because it would distribute the signing secret to every verifier.
- A shared authentication database was rejected because services must own their data and validate a
  documented HTTP contract instead.
- Implementing login in this feature was rejected because credential, password, refresh, lifetime, and
  revocation semantics are unresolved and materially expand the security surface.

## Consequences

Consumers can verify tokens independently and retain the existing 401/403 behavior. Deployments must
provide a stable public key and consistent issuer/audience configuration. A later rotation feature must
preserve `kid` compatibility and publish overlapping keys before retiring an old key.

## Migration impact

Feature 010's real admin E2E prerequisite is no longer blocked once this foundation and a separate
token-issuance feature provide a real signed token. Existing test-only JWT overrides remain valid.
