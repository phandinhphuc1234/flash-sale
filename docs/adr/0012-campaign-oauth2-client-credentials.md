# ADR 0012: OAuth2 Client Credentials for Campaign Service Identity

- **Status**: Accepted
- **Date**: 2026-07-30
- **Decision owner**: Project owner
- **Feature**: `017-campaign-management-mvp`
- **Related**: ADR 0005 (JWT trust), ADR 0006 (Authentication MVP boundary), ADR 0010
  (Inventory boundary)

## Context

Campaign administration begins with a human administrator, but scheduling and recovery call Product
and Inventory and may continue after that administrator is offline. Relaying the administrator token
would couple background correctness to a user session, broaden token propagation, and confuse the
initiating actor with the executing service.

The existing Authentication MVP is a first-party JWT issuer and explicitly does not implement a
full OAuth2/OIDC authorization server. Inventory currently uses a broad internal write scope, while
Campaign needs only variant validation, campaign allocation, and eventually campaign release.

## Decision

Authentication Service will be extended with the smallest OAuth2 Client Credentials capability
needed for a registered `campaign-service` machine client. This is not approval for Authorization
Code, browser login, consent, discovery, dynamic client registration, or a general-purpose external
identity platform.

The security model has two non-interchangeable identities:

- administrator JWT: audience `flash-sale-api`, authority `SCOPE_CAMPAIGN_ADMIN`, valid at Gateway
  and Campaign administration boundaries;
- Campaign service JWT: subject `campaign-service`, audience `flash-sale-internal-api`, short-lived,
  and valid only at internal endpoints with endpoint-specific scopes.

Authentication Service will expose `POST /oauth2/token` for `client_credentials`, authenticate the
client with HTTP Basic, store only a hash of the client secret, validate client status/grant/scopes,
and sign service access tokens with RS256 through the existing JWKS trust mechanism. The Campaign
client token lifetime is at most 300 seconds.

The Campaign client is allowed these scopes:

- `catalog.read`;
- `inventory.campaign.allocate`;
- `inventory.campaign.release`, reserved for a separately approved release feature.

Feature 017 scheduling requests only the scopes required for its Product validation and Inventory
allocation calls. Product and Inventory independently validate issuer, signature, expiration,
internal audience, `campaign-service` subject, and endpoint authority.

Campaign Service receives its client ID and secret from runtime secret configuration, caches an
access token only in memory, renews it before expiry, and never persists or logs the secret or token.
Administrator tokens are never relayed to downstream services. Campaign audit data preserves the
initiating user separately from the executing service and trace identity.

The normative wire and claim details are recorded in
`specs/017-campaign-management-mvp/contracts/service-authentication.md`.

## Alternatives considered

### Relay the administrator token

Rejected because background retries would depend on a live or retained user credential, the token
would cross more trust boundaries, and downstream authorization would represent the user rather than
Campaign Service.

### Let Product and Inventory accept `CAMPAIGN_ADMIN`

Rejected because an administrative role is broader than the capability required by one internal
operation and would couple downstream authorization to Campaign's user-facing role model.

### Use one shared secret at every downstream service

Rejected because each resource service would need to own and rotate credential verification state,
there would be no signed service identity/audience boundary, and the design would duplicate the
existing JWT trust infrastructure.

### Introduce full OAuth2/OIDC now

Rejected as unnecessary for this personal-project MVP. Only the Client Credentials grant and durable
machine-client registration required by Campaign are in scope.

## Consequences

### Positive

- recovery and background jobs do not depend on an administrator session;
- administrator and service identities remain explicit in authorization and audit data;
- Product and Inventory receive least-privilege, endpoint-specific authorization;
- service tokens reuse the established RS256/JWKS verification model;
- the internal audience prevents a service token from acting as a public/admin token.

### Costs and risks

- Authentication Service gains a new durable client registry, token endpoint, and security-critical
  test surface;
- Product and Inventory need endpoint-specific audience/scope validation;
- deployment must provision and rotate a Campaign client secret securely;
- the cross-module rollout must remain compatible so Campaign is not deployed before its issuer and
  resource servers understand the new token.

## Rollout and rollback

Roll out Authentication first, Product/Inventory validation second, Campaign token use third, and
Gateway/Campaign admin-authority compatibility last. Existing administrator token behavior remains
unchanged during the rollout.

Rollback disables Campaign scheduling/recovery calls before removing downstream service-token
acceptance or the registered client. Removing the existing administrator JWT trust path is not part
of this decision.

## Verification

Implementation is not complete until contract tests cover successful issuance and all client
rejection paths, audience/subject/scope isolation at Product and Inventory, absence of administrator
token relay, background recovery without a user session, credential redaction, and trace propagation.
