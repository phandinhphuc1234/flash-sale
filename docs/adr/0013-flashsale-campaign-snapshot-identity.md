# ADR 0013: Flash Sale Service Identity for Campaign Snapshot Access

- **Status**: Accepted
- **Date**: 2026-07-30
- **Decision owner**: Project owner
- **Feature**: `017-campaign-management-mvp`
- **Related**: ADR 0005 (JWT trust), ADR 0012 (Campaign Client Credentials)

## Context

Feature 017 exposes an internal Campaign snapshot so Flash Sale Service can build or recover its
runtime campaign state without reading Campaign, Product, or Inventory databases. The approved
Campaign service identity from ADR 0012 governs Campaign's outbound Product and Inventory calls; it
does not identify the separate service calling Campaign.

Leaving the internal caller as merely “authorized” would not define the subject, audience, or
least-privilege capability Campaign must validate.

## Decision

Authentication Service will register `flashsale-service` as a separate OAuth2 Client Credentials
client. Its credentials and tokens are independent from `campaign-service`.

For the internal Campaign snapshot call, the access token has:

- subject `flashsale-service`;
- audience `flash-sale-internal-api`;
- scope `campaign.snapshot.read`;
- the same approved short-lived RS256/JWKS trust mechanism as other internal service tokens.

Campaign Service independently validates signature, issuer, expiration, audience, subject, and
`SCOPE_campaign.snapshot.read` before returning snapshot data. Administrator tokens, Campaign
service tokens, wrong-subject tokens, and tokens without the snapshot scope do not authorize the
endpoint.

This decision registers the identity and secures the Feature 017 compatibility contract. It does
not implement the Flash Sale purchase hot path.

## Alternatives considered

### Reuse the Campaign service client

Rejected because two services would share a credential and subject, preventing independent
authorization, audit, rotation, and revocation.

### Allow an administrator token

Rejected because the snapshot is an internal service recovery contract and must not depend on a
human session or administrative role.

### Leave the endpoint authenticated but scope-neutral

Rejected because any internal service token could then read campaign runtime configuration.

## Consequences

- Authentication Service owns a second client registration and secret lifecycle.
- Campaign Service needs an endpoint-specific internal audience, subject, and scope policy.
- Flash Sale Service will obtain and cache its own short-lived access token when its recovery slice
  is implemented.
- Contract tests must prove token substitution, wrong audience/subject, expiry, and missing scope are
  denied without returning snapshot data.

## Contract

The normative flow and claim matrix are defined in
`specs/017-campaign-management-mvp/contracts/service-authentication.md`.
