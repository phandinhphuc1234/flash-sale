# Quickstart: Validate Cart Service Scaffold

Run all commands from the repository root.

## 1. Confirm governance artifacts

```powershell
Get-Content .specify/feature.json
Get-Content docs/adr/0002-cart-service-boundary.md
```

Expected: feature `007-cart-service-scaffold`, approved spec/plan, and accepted ADR 0002.

## 2. Verify the Cart module

```powershell
.\mvnw.cmd -pl services/cart-service -am verify
```

Expected: the Cart application-context test passes and the module builds successfully.

## 3. Render the base local topology

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml --profile apps config
```

Expected: `cart-service` appears once on the internal network with container port `8080` and no host port.

## 4. Render the development topology

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps config
```

Expected: Cart additionally maps host port `18089` to container port `8080`.

These commands only render configuration; they do not pull, build, start, or remove containers.

## 5. Verify the entire monorepo

```powershell
.\mvnw.cmd clean verify
```

Expected: all ten service modules and all ten Spring application-context tests pass.

## 6. Inspect scope

Confirm that Cart contains exactly one production application class, one context test, 17 empty Clean Architecture markers, an empty Liquibase master, and no business API, gateway route, persistence implementation, event contract, Redis integration, or business changeset.

## Existing PostgreSQL Volume Note

`infra/docker/postgres/init/01-create-databases.sql` runs only when PostgreSQL initializes a fresh data volume. If a developer already has a local volume, `cart_db` will not appear automatically. Create it explicitly using an approved non-destructive database administration step; do not delete the existing volume merely to make the bootstrap script rerun.
