# Shared Docker Infrastructure

`infra/docker/` owns the shared local Docker Compose topology for the monorepo. It is a developer
runtime, not the production deployment source of truth.

## Files

```text
infra/docker/
├── compose.yml                 # Local baseline: platform services + app profile
├── compose.dev.yml             # Optional local debugging override
├── .env.example                # Safe placeholder defaults
├── kafka/
│   └── init-campaign-topics.sh  # Approved Feature 017 topic provisioning
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

The baseline also provides a local Confluent Schema Registry at `http://localhost:8081`. It stores
schemas in Kafka's compacted `_schemas` topic and is intentionally not required by application
containers until an approved Kafka serialization feature adopts it.

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

Authentication trust and browser sessions are owned by authentication-service (Feature 015). Compose
passes the issuer/audience to consumers, mounts the RSA key directory read-only into Auth, and passes
the dedicated throttle HMAC secret only to Auth. Gateway receives the exact trusted-origin list for
credentialed auth CORS. Keep the private key and real secrets outside Git.

For local use, generate a key pair outside the repository and put both PEM files in the directory
referenced by `AUTH_JWT_KEY_DIR`:

```powershell
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -pubout -in jwt-private.pem -out jwt-public.pem
```

Never commit the key files, `.env`, or a private key in any image layer. Set
`AUTH_THROTTLE_HMAC_SECRET` to a separate Base64 secret generated from at least 32 random bytes.

Authentication also requires its service-owned Liquibase schema before the first application
startup. The normal Auth container keeps `SPRING_LIQUIBASE_ENABLED=false` so multiple replicas do
not race migrations; apply the migration once from the built image:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml build authentication-service
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml run --rm --no-deps `
  -e SPRING_LIQUIBASE_ENABLED=true `
  -e SPRING_MAIN_KEEP_ALIVE=false `
  authentication-service --spring.main.web-application-type=none
```

Repeat the command safely after a deployment; Liquibase records completed changesets in
`auth_db.databasechangelog`. Do not delete the PostgreSQL volume to force a migration rerun.

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
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml up -d postgres redis kafka schema-registry

curl http://localhost:8081/subjects
```

### Kafka UI

The optional `tools` profile provides Kafbat UI for inspecting the local Kafka cluster and
Confluent Schema Registry. It is loopback-bound and is not part of the default application
topology.

Start it with Kafka and Schema Registry:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile tools up -d kafka-ui
```

Open `http://localhost:8088`. The UI connects to `kafka:9092` and
`http://schema-registry:8081` over the private `flash-sale-net` network. The image is pinned in
`.env.example`; update `KAFKA_UI_IMAGE` only when intentionally upgrading the local tool.

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
| `inventory-service` | `18088` |
| `cart-service` | `18089` |

The Gateway is loopback-bound by default (`GATEWAY_BIND_ADDRESS=127.0.0.1`). For a VPS, terminate
HTTPS at a host reverse proxy or managed load balancer and proxy only to this loopback port. Do not
set the bind address to `0.0.0.0` unless the host firewall and TLS boundary are intentionally managed
outside this repository. PostgreSQL, Redis, and Kafka remain loopback-bound by default.

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

## Inventory database and one-off migration

`inventory-service` owns `inventory_db` and its changelog under
`services/inventory-service/src/main/resources/db/changelog/`. Normal Inventory replicas keep
`SPRING_LIQUIBASE_ENABLED=false`; apply the schema through a one-off non-web process before starting
the service:

PostgreSQL initialization scripts only run for a new volume. If the volume predates Inventory,
check for the logical database and create only that missing database; do not delete the volume:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml exec -T postgres psql -U flashsale -d flashsale_admin -tAc "SELECT 1 FROM pg_database WHERE datname = 'inventory_db'"
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml exec -T postgres createdb -U flashsale -O flashsale inventory_db
```

Run the `createdb` command only when the check returns no row.

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml build inventory-service
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml run --rm --no-deps `
  -e SPRING_LIQUIBASE_ENABLED=true `
  -e SPRING_MAIN_KEEP_ALIVE=false `
  inventory-service --spring.main.web-application-type=none
```

The command is repeatable because Liquibase records completed changesets in
`inventory_db.databasechangelog`. Do not delete the PostgreSQL volume to rerun a migration.

## Campaign database and one-off migration

`campaign-service` owns `campaign_db` and its changelog under
`services/campaign-service/src/main/resources/db/changelog/`. The normal Campaign container keeps
`SPRING_LIQUIBASE_ENABLED=false`; apply its schema once before starting the application profile:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml build campaign-service
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml run --rm --no-deps `
  -e SPRING_LIQUIBASE_ENABLED=true `
  -e SPRING_MAIN_KEEP_ALIVE=false `
  campaign-service --spring.main.web-application-type=none
```

The migration command is repeatable because Liquibase records completed changesets in
`campaign_db.databasechangelog`. Compose supplies Campaign with the PostgreSQL, Kafka, Schema
Registry, Product, and Inventory service endpoints, but it does not make a remote HTTP call during
the one-off migration.

## Campaign lifecycle Kafka topic

Feature 017 currently provisions only the approved `campaign.lifecycle.v1` topic. Start Kafka and
Schema Registry, then run the root-owned provisioning script:

```bash
bash infra/docker/kafka/init-campaign-topics.sh
```

The script is idempotent and verifies three partitions with replication factor one. Schema
registration is deliberately controlled by the contract-module/Registry rollout process; the
Campaign producer keeps `auto.register.schemas=false` outside explicitly managed experiments.

## Stop containers

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps down
```

Delete local volumes only when you intentionally want a clean platform state:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps down -v
```

## Current limitations

- Application containers set `SPRING_LIQUIBASE_ENABLED=false` because schema changes run as explicit
  one-off processes. `authentication-service`, `product-service`, and `inventory-service` have
  service-owned migrations; the remaining service shells still have empty changelogs until approved
  schema features.
- PostgreSQL bootstrap creates logical local databases only; service-owned business migrations
  remain under `services/<service>/src/main/resources/db/changelog/`.
