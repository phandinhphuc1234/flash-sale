# Quickstart: Product Catalog Migration

Run all commands from the repository root. These steps target local development only and never delete an existing PostgreSQL volume.

## 1. Fast Product verification

The ordinary context test is intentionally database-independent:

```powershell
.\mvnw.cmd -pl services/product-service -am test
```

## 2. Real PostgreSQL integration verification

This opt-in profile requires a working Docker engine and starts an isolated PostgreSQL Testcontainer:

```powershell
.\mvnw.cmd -pl services/product-service -am -Pproduct-migration-it verify
```

It verifies migration application, tables, indexes, constraints, valid and invalid writes, Liquibase ledger state, and a second idempotent Liquibase invocation.

## 3. Validate Compose rendering

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml --profile apps config
```

Confirm that `product-service` has the `product_db` datasource and that its normal process still has `SPRING_LIQUIBASE_ENABLED=false`.

## 4. Start local PostgreSQL

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml up -d postgres
```

The bootstrap script creates `product_db` only when the PostgreSQL volume is first initialized. For an older volume, check before creating it:

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml exec -T postgres psql -U flashsale -d flashsale_admin -tAc "SELECT 1 FROM pg_database WHERE datname = 'product_db'"
```

If that command returns no row, create only the missing logical database:

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml exec -T postgres createdb -U flashsale -O flashsale product_db
```

Do not use `down -v` merely to recreate the database.

## 5. Inspect before applying

If `product_db` already contains unmanaged tables with one of the five target names, stop and investigate rather than dropping or adopting them:

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml exec -T postgres psql -U flashsale -d product_db -c "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;"
```

## 6. Build and run the one-off migration

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml build product-service
```

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml run --rm --no-deps -e SPRING_LIQUIBASE_ENABLED=true -e SPRING_MAIN_KEEP_ALIVE=false product-service --spring.main.web-application-type=none
```

The normal Product application container remains migration-disabled. This one-off process is the local equivalent of a future Kubernetes migration Job.

## 7. Inspect migration evidence

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml exec -T postgres psql -U flashsale -d product_db -c "SELECT id, author, exectype, orderexecuted FROM databasechangelog ORDER BY orderexecuted;"
```

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml exec -T postgres psql -U flashsale -d product_db -c "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;"
```

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml exec -T postgres psql -U flashsale -d product_db -c "SELECT locked FROM databasechangeloglock;"
```

## 8. Verify idempotency

Run the same one-off migration command again. Liquibase must report no pending changes, and `databasechangelog` must still contain exactly one Product changeset.

## 9. Final repository verification

```powershell
.\mvnw.cmd -pl services/product-service -am verify
```

```powershell
.\mvnw.cmd clean verify
```

## Rollback warning

The changeset contains rollback statements for disposable migration-test databases. Do not roll back the successfully initialized local `product_db` after data is added. Use forward changesets for normal corrections; any destructive rollback against shared or valuable data requires a separate approved plan.
