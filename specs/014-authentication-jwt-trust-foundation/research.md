# Research: Authentication JWT Trust Foundation

## Decisions

### RS256 with JWKS

Use an RSA public-key JWKS contract. Resource servers can cache public keys and verify tokens locally;
the private key never crosses a service boundary.

### Explicit issuer and audience validators

Configure each resource server with the canonical issuer and JWKS URI, then add an audience validator.
Spring Security validates the signature and standard time claims while the application contract keeps
audience acceptance explicit.

### Fail-closed key configuration

Do not generate an ephemeral key when configuration is absent. A restarted issuer with a new implicit
key would invalidate tokens unexpectedly and could create an untrusted deployment.

### No token issuance in this feature

Login, password verification, refresh, revocation, and token lifetime policy are separate business and
security decisions. This feature publishes trust material and verifies it only.

## Alternatives considered

- Symmetric HS256 was rejected because every verifier would need the signing secret.
- OpenID discovery was deferred because the approved contract names a stable JWKS path and avoids a
  second discovery contract for this MVP.
- A shared JWT decoder library was deferred; service-local validation keeps deployment configuration
  and trust ownership explicit without sharing business models.
