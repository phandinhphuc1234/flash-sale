# Authentication service

Feature 015 provides the browser Authentication MVP:

- public account registration;
- password login with Argon2id proof;
- RS256 access-token issuance and public JWKS;
- HttpOnly rotating refresh credentials;
- current/all-session logout and refresh-reuse detection;
- Redis account throttling and Actuator health/metrics endpoints.

The service owns its PostgreSQL schema and Liquibase migrations. It does not act as a general
OAuth2/OIDC authorization server and it does not perform automatic signing-key rotation.

## Runtime requirements

Configure PostgreSQL, Redis, a deployment-provided RSA key pair, and a separate Base64 HMAC secret
for account throttling. The private key must be PKCS#8 RSA PEM and the public key must be X.509
SubjectPublicKeyInfo PEM. The public and private keys must contain the same RSA modulus.

Required environment values for a real deployment include:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_DATA_REDIS_HOST
SPRING_DATA_REDIS_PASSWORD
JWT_PUBLIC_KEY_LOCATION
JWT_PRIVATE_KEY_LOCATION
AUTH_THROTTLE_HMAC_SECRET
AUTH_TRUSTED_ORIGINS
```

`JWT_PUBLIC_KEY_PEM` and `JWT_PRIVATE_KEY_PEM` are supported for focused tests, but mounted key
files are preferred for a VPS. Never commit `.env`, private keys, access tokens, refresh cookies,
or passwords.

## Migration before startup

The normal application container uses `SPRING_LIQUIBASE_ENABLED=false` so replicas do not race
migrations. Run the service-owned migration once before starting the application:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml build authentication-service
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml run --rm --no-deps `
  -e SPRING_LIQUIBASE_ENABLED=true `
  -e SPRING_MAIN_KEEP_ALIVE=false `
  authentication-service --spring.main.web-application-type=none
```

## Endpoints

Public traffic should enter through `api-gateway`. The service exposes:

- `/.well-known/jwks.json` — public verification key only;
- `/api/v1/auth/register`, `/login`, `/refresh`, `/logout`;
- `/api/v1/auth/logout-all` — bearer-protected;
- `/actuator/health/liveness`, `/actuator/health/readiness`, and `/actuator/prometheus`.

For the complete local smoke flow, see
[`specs/015-authentication-session-mvp/quickstart.md`](../../specs/015-authentication-session-mvp/quickstart.md).
