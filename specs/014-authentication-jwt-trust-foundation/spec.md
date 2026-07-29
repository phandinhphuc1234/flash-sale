# Feature Specification: Authentication JWT Trust Foundation

**Feature Branch**: `014-authentication-jwt-trust-foundation`
**Created**: 2026-07-25
**Status**: Verified
**Input**: User description: "The next step is the separate Authentication feature. It cannot be implemented safely until we approve its JWT trust contract."
**Business Owner**: Flash Sale platform owner
**Required Reviewers**: platform/security reviewer

## Problem and Scope

### Problem Statement

The gateway and product-service can only use test JWTs today. A real request cannot be trusted until
the authentication boundary publishes a stable verification contract that downstream services can
validate independently.

### In Scope

- Define and publish the canonical JWT trust contract for this repository.
- Expose the authentication service's public signing key through a JWKS endpoint.
- Make api-gateway and product-service validate signature, issuer, audience, and standard time claims
  against the canonical contract.
- Preserve the existing `CATALOG_ADMIN` authority mapping and 401/403 distinction.
- Keep signing keys outside the repository and document local configuration.

### Out of Scope

- User registration, password storage, login UI/API, refresh tokens, logout, revocation, MFA, and
  account recovery.
- Issuing production access tokens. A separate approved Authentication issuance feature is required.
- Key rotation automation, token lifetime policy, or clock-skew policy beyond the resource-server
  library's standard validation of supplied `iat`/`exp` claims.

## User Scenarios & Testing

### User Story 1 - Publish a verifiable signing key (Priority: P1)

As a downstream service, I want to retrieve the authentication service's public JWKS so that I can
verify bearer tokens without sharing a private key.

**Why this priority**: Without a public key contract, no service can safely trust a real token.

**Independent Test**: A request to `GET /.well-known/jwks.json` returns a valid JWKS containing the
active RSA public key and its `kid`, while private key material is never returned.

**Acceptance Scenarios**:

1. **Given** a configured public RSA key, **When** the JWKS endpoint is called, **Then** it returns
   HTTP 200 with one `RSA` signing key using `RS256` and a non-empty `kid`.
2. **Given** the public key configuration is absent or malformed, **When** the service starts,
   **Then** startup fails closed with a configuration error rather than publishing an unverifiable
   or generated key.

### User Story 2 - Validate the canonical token contract (Priority: P1)

As the gateway or product-service, I want to validate a bearer JWT consistently so that a token
signed by the authentication service is accepted and an untrusted token is rejected.

**Why this priority**: Independent verification prevents a compromised edge or caller-supplied header
from becoming an identity source.

**Independent Test**: Resource-server tests exercise valid, wrong-signature, wrong-issuer,
wrong-audience, expired, and missing-claim tokens.

**Acceptance Scenarios**:

1. **Given** a token signed with the active RSA key, `iss` equal to
   `http://authentication-service:8080`, `aud` containing `flash-sale-api`, and required claims,
   **When** an admin request crosses gateway and product-service, **Then** each boundary accepts the
   verified identity and `CATALOG_ADMIN` is available as an authority.
2. **Given** a token with an invalid signature, issuer, audience, or expiry, **When** it reaches a
   protected route, **Then** the request is treated as unauthenticated and returns the existing 401
   error contract.
3. **Given** a valid token without `CATALOG_ADMIN`, **When** it reaches an admin route, **Then** the
   request is authenticated but forbidden and returns the existing 403 error contract.

## Requirements

### Functional Requirements

- **FR-001**: The canonical signing algorithm MUST be `RS256`.
- **FR-002**: The canonical issuer MUST be `http://authentication-service:8080` and the accepted
  audience MUST include `flash-sale-api`.
- **FR-003**: The JWKS endpoint MUST be `GET /.well-known/jwks.json` and MUST expose only public
  signing material.
- **FR-004**: Each JWT MUST identify its signing key with a non-empty `kid`; the JWKS key MUST expose
  the same identifier, `kty=RSA`, `use=sig`, and `alg=RS256`.
- **FR-005**: Required claims are non-blank `sub`, exact `iss`, `aud` containing `flash-sale-api`,
  `iat`, `exp`, and an `authorities` collection when authorities are present. `CATALOG_ADMIN` is the
  canonical admin authority.
- **FR-006**: Gateway and product-service MUST verify the signature and issuer/audience before
  creating an authenticated principal.
- **FR-007**: Invalid authentication MUST remain a 401 response; an authenticated principal lacking
  the required route authority MUST remain a 403 response.
- **FR-008**: Private signing keys MUST be supplied through deployment secret configuration or a
  mounted secret and MUST NOT be committed to source control or returned by the JWKS endpoint.
- **FR-009**: Missing or malformed key configuration MUST fail closed; the service MUST NOT silently
  generate an ephemeral production key.
- **FR-010**: The endpoint and validation path MUST preserve the repository's health, Prometheus,
  and trace-ID conventions.

### Key Entities

- **JWT Trust Contract**: The stable issuer, audience, algorithm, claim, and error semantics shared
  by token issuers and resource servers.
- **JWKS Key**: Public RSA verification material identified by `kid`; it contains no private key.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% of contract tests agree on issuer, audience, algorithm, JWKS path, `kid`, and
  authority semantics across authentication-service, api-gateway, and product-service.
- **SC-002**: No protected request with a wrong signature, issuer, audience, or expiry reaches a
  product handler in the verification test suite.
- **SC-003**: A valid `CATALOG_ADMIN` token can pass both resource-server boundaries in an isolated
  integration test without caller-supplied actor headers.
- **SC-004**: No private key, token secret, or raw credential appears in repository-tracked files,
  test reports, or JWKS responses.

## Dependencies and Compatibility

- Feature 010's blocked prerequisite contract is completed by this feature.
- Existing gateway/product test-only JWT overrides remain supported.
- Existing public catalog and protected admin HTTP paths do not change.

## Assumptions

- The service DNS name `authentication-service` resolves through Kubernetes Service/DNS and local
  Compose overrides may replace the issuer/JWKS URI.
- Resource servers validate supplied `iat` and `exp` claims using Spring Security's standard clock
  validation. A custom lifetime, clock-skew, refresh, or revocation policy requires a later feature.
- The first deployment uses one active signing key. `kid` is still mandatory so a later approved
  rotation can publish overlapping keys without changing the consumer contract.

## Constitutional Constraints

- **Service ownership**: authentication-service owns signing-key publication; gateway and product
  validate independently and access no authentication database.
- **External ingress**: JWKS is a narrowly public metadata endpoint; user-facing traffic still enters
  through api-gateway.
- **API/event contracts**: the JWKS contract is documented under this feature; no Kafka contract is
  introduced.
- **Durable and hot-path data**: no product/order/flash-sale data or Redis Lua behavior changes.
- **Messaging reliability**: no Kafka producer/consumer is added.
- **Root infrastructure ownership**: only secret placeholders and documentation may change under
  `infra/`; service runtime configuration remains with each service.
- **Observability**: existing Actuator endpoints, Micrometer Prometheus auto-configuration, and trace
  propagation remain enabled; no manual Prometheus registry is added.
- **Verification**: unit/contract tests for JWKS and validator behavior plus affected module Maven
  verification apply; load testing is not applicable to this metadata foundation.
- **Architecture decisions**: ADR 0005 records the new trust boundary and its deferred issuance scope.

## Approval and History

- 2026-07-25 — Draft created from the approved JWT trust decision (RS256, issuer, audience, JWKS path,
  claims, and 401/403 semantics).
- 2026-07-25 — Approved by Flash Sale platform owner (user) for implementation of the trust foundation;
  token issuance remains explicitly out of scope.
- 2026-07-25 — Verified after authentication, gateway, product, Compose, and artifact consistency checks.
