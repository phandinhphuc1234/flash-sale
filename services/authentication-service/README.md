# Authentication Service

Authentication Service owns human accounts, credentials, sessions, refresh-token rotation, JWT
issuance, JWKS publication, and service client credentials. Other services validate access tokens;
they do not query authentication tables.

## HTTP boundaries

| Boundary | Endpoint | Purpose |
|---|---|---|
| Public | `POST /api/v1/auth/register` | Create a shopper account |
| Public | `POST /api/v1/auth/login` | Authenticate and create a session |
| Session | `POST /api/v1/auth/refresh` | Rotate the refresh credential |
| Session | `POST /api/v1/auth/logout` | Revoke the current session |
| Authenticated | `POST /api/v1/auth/logout-all` | Revoke all owned sessions |
| Identity trust | `GET /.well-known/jwks.json` | Publish RSA public key material |
| Internal | `POST /oauth2/token` | Issue scoped service client-credentials JWTs |

Refresh credentials are carried by the configured secure cookie policy. Service tokens require an
approved client ID/secret and scope; they are not shopper tokens.

## Security model

- passwords are hashed with configurable Argon2 parameters;
- RS256 private keys remain outside Git and container image layers;
- consumers validate issuer, audience, signature, expiry, and route-specific authorities;
- login throttling uses Redis plus a dedicated HMAC secret;
- refresh token rotation/reuse detection and logout state are durable;
- trusted browser origins are explicit; CORS is not a substitute for authentication.

The administrator bootstrap is disabled on the long-running deployment. When required, the
reviewed one-off bootstrap Job/script receives the password interactively and does not print or
persist it in repository files.

## Data and runtime

Authentication owns `auth_db` and its Liquibase changelog. Normal replicas keep migrations disabled
so multiple pods do not race the same schema change. Redis stores throttle/runtime state, not the
durable account/session source of truth.

Important environment groups:

- PostgreSQL and `LIQUIBASE_ENABLED`;
- Redis and throttle thresholds/secrets;
- RSA key locations or injected PEM values, issuer, audience, and key ID;
- refresh cookie security and trusted origins;
- internal service clients/scopes;
- retention/cleanup and optional bootstrap settings;
- `API_DOCS_ENABLED` (local opt-in only).

The client secret configured here must exactly match the consumer service's secret. A mismatch
returns invalid client credentials even when both containers are otherwise healthy.

## Run and verify

```powershell
.\mvnw.cmd -pl services/authentication-service -am verify
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps up -d --build authentication-service
```

Apply the service-owned Liquibase migration explicitly before normal startup; follow
[`infra/docker/README.md`](../../infra/docker/README.md). Full payload examples are in the
[frontend integration guide](../../docs/api/frontend-integration-guide.md).

## Troubleshooting

- `Invalid credentials`: verify the account identifier/password and inspect lock/throttle state;
  do not conflate it with browser transport errors.
- `Authentication service unavailable`: verify Gateway routing and the Auth readiness endpoint.
- Browser `Failed to fetch`: inspect Gateway CORS preflight and exact origin before changing login
  business logic.
- Internal OAuth `401`: verify the same client secret exists on both sides and the requested scope
  is approved for that client.
- JWT accepted by Auth but rejected downstream: compare issuer, audience, JWKS URI, token type, and
  clock synchronization.
