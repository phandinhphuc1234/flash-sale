# Local Compose Smoke Validation

## Validation record

| Field | Value |
|---|---|
| Date | 2026-07-29 |
| Environment | Windows, Docker Desktop/Engine 29.4.0, Java 21 |
| Compose project | `flash-sale-vps` |
| Compose files | `infra/docker/compose.yml`, `infra/docker/compose.dev.yml` |
| Application profile | `apps` with `docker` Spring profile |
| Public client boundary | `http://127.0.0.1:18080` |
| CI/PR reference | Local validation only |
| Overall result | PASS |

No password, HMAC secret, JWT private key, access token, refresh token, or raw cookie value was
recorded in this document.

## Scope

Validated runtime path:

```text
Client
  -> API Gateway
  -> Authentication Service
  -> Product Service
  -> Inventory Service
  -> PostgreSQL / Redis
```

Validated capabilities:

- full Maven reactor after synchronizing the Gateway error taxonomy test;
- application image builds for Gateway, Authentication, Product, and Inventory;
- one-off Liquibase execution for Authentication, Product, and Inventory;
- application health and Auth JWKS publication;
- registration, login, access-token validation, refresh rotation, authorization, and logout-all;
- Gateway-to-Product and Gateway-to-Inventory routing with real downstream databases;
- Gateway rate-limit fail-open and Authentication throttle fail-closed while Redis is unavailable;
- Redis restart and automatic client reconnection.

Not claimed by this validation:

- concurrent refresh-reuse database proof;
- Inventory concurrent allocation/reconciliation quickstart scenarios;
- Kafka event publication or consumption;
- k6 load measurement;
- Kubernetes validation.

## Build gate

### Gateway error taxonomy correction

`GatewayErrorCodeTests` now expects all nine Gateway-owned codes and includes:

```text
INVENTORY_ADMIN_REQUIRED -> HTTP 403 -> INVENTORY_ADMIN authority is required
```

No Gateway production behavior changed in this correction.

### Full reactor

Command:

```powershell
.\mvnw.cmd clean verify
```

Result:

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `api-gateway` | 177 | 0 | 0 | 0 |
| `authentication-service` | 29 | 0 | 0 | 0 |
| `product-service` | 32 | 0 | 0 | 0 |
| `inventory-service` | 13 | 0 | 0 | 0 |
| Six scaffold services | 6 | 0 | 0 | 0 |
| **Total** | **257** | **0** | **0** | **0** |

- Exit code: `0`.
- Maven result: `BUILD SUCCESS`.
- Reactor modules: `12/12` successful.
- Elapsed time: 518.8 seconds for the final post-fix run.

## Image build and database migration

### Images

Command:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps build `
  api-gateway authentication-service product-service inventory-service
```

Result: exit code `0`; all four local images built successfully.

### Backing services

PostgreSQL 17, Redis 7.4, and Kafka 4.0 reported `healthy` before application startup.

### One-off Liquibase runs

Each migration used the owning service image with application replicas still configured with
Liquibase disabled:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml run --rm --no-deps `
  -e SPRING_LIQUIBASE_ENABLED=true `
  -e SPRING_MAIN_KEEP_ALIVE=false `
  <service> --spring.main.web-application-type=none
```

| Service | Database | Changesets | Result |
|---|---|---:|---|
| Authentication | `auth_db` | 2 previously applied | Up to date, exit `0` |
| Product | `product_db` | 2 previously applied | Up to date, exit `0` |
| Inventory | `inventory_db` | 1 previously applied | Up to date, exit `0` |

## Runtime health and JWKS

| Check | Result |
|---|---|
| Gateway `/actuator/health` | 200 |
| Authentication `/actuator/health` | 200 |
| Product `/actuator/health` | 200 |
| Inventory `/actuator/health` | 200 |
| Authentication `/.well-known/jwks.json` | 200 |

JWKS assertions:

- exactly one configured key;
- `kty=RSA`;
- `use=sig`;
- `alg=RS256`;
- non-empty `kid`;
- no `d`, `p`, `q`, `dp`, `dq`, or `qi` private fields.

## End-to-end JWT and service flow

A unique temporary account and password were generated in memory. The account was registered as a
normal user, promoted to `ROLE_ADMIN` only through the local smoke database fixture, exercised, and
deleted afterward. No credential or token value was printed.

| Step | Expected | Observed |
|---|---|---|
| Public Product catalog through Gateway | 200 | 200 |
| Anonymous Inventory admin request | `401 UNAUTHENTICATED` | Passed |
| Register account | 201, `ROLE_USER` | Passed |
| Login | 200, bearer token and refresh cookie | Passed |
| Access-token issuer | `http://authentication-service:8080` | Passed |
| Access-token audience | `flash-sale-api` | Passed |
| Initial authorities | `ROLE_USER` | Passed |
| Refresh with trusted Origin/Referer | 200 and rotated access token/cookie | Passed |
| User calls Inventory admin | `403 INVENTORY_ADMIN_REQUIRED` | Passed |
| Admin login after local fixture promotion | `ROLE_ADMIN,CATALOG_ADMIN,INVENTORY_ADMIN` | Passed |
| Product admin query through Gateway | 200 | Passed |
| Missing Inventory variant through Gateway | `404 INVENTORY_NOT_FOUND` | Passed |
| Logout current session with admin JWT | 204 | Passed |
| Refresh after logout | 401 | Passed |
| Delete temporary account fixture | Successful | Passed |

The Product admin request proves that Gateway and Product validate the Auth-issued JWT. The
Inventory not-found request proves that Gateway and Inventory validate the same token and that the
request reaches the Inventory-owned PostgreSQL state.

## Trace contract check

Authentication returned the same caller trace value in `X-Trace-Id` both directly and through
Gateway. Authentication non-empty bodies also contained the trace ID.

Product and Inventory currently receive the trace request header but do not add a client-visible
response trace header. This matches Feature 013's explicit rule that its catalog correlation work
does not introduce a new client response header; it is not recorded as a smoke failure here.

## Redis-down failure and recovery

Procedure:

1. Confirm Product catalog returns 200 with Redis healthy.
2. Stop only Redis.
3. Stop Gateway and restart it with `--no-deps` while Redis remains stopped.
4. Verify Gateway health/liveness/readiness.
5. Call Product catalog through the rate-limited Gateway route.
6. Attempt Authentication login through Gateway.
7. Restart Redis and retry Auth with fresh test identities until the client reconnects.

Observed results:

| Check | Result |
|---|---|
| Redis-up Product catalog | 200 |
| Gateway aggregate health with Redis down | 200 |
| Gateway liveness with Redis down | 200 |
| Gateway readiness with Redis down | 200 |
| Product catalog with Redis down | 200, Gateway fail-open |
| Auth login with Redis down | `503 AUTHENTICATION_UNAVAILABLE`, Auth fail-closed |
| Redis after restart | `healthy` |
| Auth recovery | `401 AUTH_INVALID_CREDENTIALS` for unknown account after reconnect |

Recovery was not instantaneous. The first immediate HTTP attempt ended while the connection was
being re-established, the second returned the expected temporary `503`, and the third returned the
normal `401` approximately ten seconds after Redis became healthy. No database reconciliation or
application-container restart was required.

## Final topology state

At the end of validation:

- PostgreSQL, Redis, and Kafka were running and healthy;
- Gateway, Authentication, Product, and Inventory were running;
- Gateway was loopback-bound on port `18080`;
- Authentication, Product, and Inventory were restored to internal-only port `8080` with no host publishing;
- Redis and PostgreSQL remained loopback-bound;
- the temporary authentication account was deleted.
