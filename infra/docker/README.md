# Shared Docker Infrastructure

`infra/docker/` owns the shared local Docker Compose topology for the monorepo. It is a developer
runtime, not the production deployment source of truth.

## Files

```text
infra/docker/
├── compose.yml                 # Local baseline: platform services + app profile
├── compose.dev.yml             # Optional local debugging override
├── .env.example                # Safe placeholder defaults
└── postgres/
    └── init/
        └── 01-create-databases.sql
```

Service-specific Dockerfiles stay under `services/<service>/Dockerfile` so each service can build
an image independently. Shared Compose topology stays here.

## Compose file policy

Use:

- `compose.yml` for the local baseline.
- `compose.dev.yml` when you want direct host ports for non-gateway services during debugging.
- Compose profiles for optional application services.

Do not add `compose.prod.yml` for this repository. Production/staging deployment should be modeled
by future Kubernetes resources under `infra/k8s/`.

## Prepare local environment

```powershell
Copy-Item infra/docker/.env.example infra/docker/.env
```

```bash
cp infra/docker/.env.example infra/docker/.env
```

Real `.env` files are ignored by git. Keep only placeholder examples committed.

The Compose file intentionally requires `POSTGRES_PASSWORD` and `REDIS_PASSWORD`; it no longer
falls back to the old development password. Before starting the stack, replace every
`REPLACE_WITH_...` value in `infra/docker/.env` with a unique secret. Keep the file private on a
VPS and never commit it.

Redis is protected with `requirepass`, and the Gateway receives the same password through
`SPRING_DATA_REDIS_PASSWORD`. PostgreSQL, Redis, and Kafka host bindings default to `127.0.0.1`, so
the backing services are not published on public interfaces. Containers still communicate through
the private `flash-sale-net` network.

Generate suitable values with a password manager or a cryptographically secure generator, for
example:

```powershell
openssl rand -base64 48
```

The Gateway's `JWT_JWK_SET_URI` is currently only a configuration contract. Feature 013 does not
implement JWT signing or the authentication-service JWKS endpoint; that work belongs to the
separate authentication feature.

## Validate configuration

Baseline:

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml config
```

Development override:

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps config
```

## Run only platform services

Use this while coding one service from the IDE or Maven:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml up -d postgres redis kafka
```

Then run the service you are actively developing outside Docker, for example:

```powershell
.\mvnw.cmd -pl services/order-service -am spring-boot:run
```

## Run the application topology

Default app topology exposes only `api-gateway` to the host:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps up --build
```

Open:

```text
http://localhost:8080/actuator/health
```

## Run with direct service debug ports

Use the development override only when you want to call a service directly from the host:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps up --build
```

Debug ports:

| Service | Host port |
|---|---:|
| `authentication-service` | `18081` |
| `product-service` | `18082` |
| `campaign-service` | `18083` |
| `flashsale-service` | `18084` |
| `order-service` | `18085` |
| `payment-service` | `18086` |
| `notification-service` | `18087` |
| `chatting-service` | `18088` |
| `cart-service` | `18089` |

## Cart local runtime boundary

The base topology keeps `cart-service` internal. Other containers on `flash-sale-net` resolve it as
`http://cart-service:8080`; only the development override publishes its configurable debug port,
which defaults to `localhost:18089`.

PostgreSQL bootstrap creates only the empty logical database `cart_db`. Cart tables and business
schema migrations remain owned by `cart-service` and are not part of the shared bootstrap script.
The initialization scripts run only when PostgreSQL creates a fresh data volume, so an existing
volume will not gain `cart_db` automatically. Create the database with an approved non-destructive
administration step; do not delete an existing volume merely to rerun initialization.

## Product database and one-off migration

`product-service` owns `product_db` and the Product catalog changelog under:

```text
services/product-service/src/main/resources/db/changelog/
```

Compose provides only `product-service` with:

```text
jdbc:postgresql://postgres:5432/product_db
```

Normal Product replicas keep `SPRING_LIQUIBASE_ENABLED=false`. Apply Product migrations explicitly
with a one-off non-web Product process; this mirrors the future Kubernetes migration Job model.

Start PostgreSQL:

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml up -d postgres
```

For an existing PostgreSQL volume, check whether the bootstrap-created database exists:

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml exec -T postgres psql -U flashsale -d flashsale_admin -tAc "SELECT 1 FROM pg_database WHERE datname = 'product_db'"
```

If no row is returned, create only the missing logical database:

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml exec -T postgres createdb -U flashsale -O flashsale product_db
```

Do not delete the PostgreSQL volume to rerun bootstrap. Before migration, inspect existing Product
tables and stop if target-name tables exist without matching Liquibase history.

Build and apply the migration:

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml build product-service
```

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml run --rm --no-deps -e SPRING_LIQUIBASE_ENABLED=true -e SPRING_MAIN_KEEP_ALIVE=false product-service --spring.main.web-application-type=none
```

Running the same one-off command again is safe: Liquibase reads `databasechangelog` and executes no
already-recorded changeset.

## Stop containers

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps down
```

Delete local volumes only when you intentionally want a clean platform state:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps down -v
```

## Current limitations

- Application service code does not yet implement PostgreSQL/JPA/Kafka/Redis adapters.
- Application containers set `SPRING_LIQUIBASE_ENABLED=false` because schema changes run as explicit
  one-off processes. `product-service` now has JDBC/PostgreSQL runtime and its first catalog
  changeset; the other service shells still have empty changelogs until approved schema features.
- PostgreSQL bootstrap creates logical local databases only; service-owned business migrations
  remain under `services/<service>/src/main/resources/db/changelog/`.
