# Quickstart: Container Compose and Kubernetes Readiness

## Prerequisites

- Docker Desktop or Docker Engine with Docker Compose v2.
- Java 21 and Maven Wrapper only if running services directly outside containers.

## 1. Prepare local environment file

Copy the example file:

```powershell
Copy-Item infra/docker/.env.example infra/docker/.env
```

On bash:

```bash
cp infra/docker/.env.example infra/docker/.env
```

The real `.env` file is ignored by git.

## 2. Validate Compose rendering

Baseline:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml config
```

Development override:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps config
```

Expected result: both commands exit successfully and print normalized Compose configuration.

## 3. Start only platform services

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml up -d postgres redis kafka
```

Expected result: PostgreSQL, Redis, and Kafka containers start without building application service images.

## 4. Start full local app topology

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps up --build
```

Expected result: all service images build from their service-local Dockerfiles; only `api-gateway` is exposed on host port `8080`.

## 5. Start with development debug ports

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps up --build
```

Expected result: non-gateway services are additionally exposed on host ports `18081` through `18088` for debugging.

## 6. Stop local containers

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps down
```

Add `-v` only when you intentionally want to delete local volumes:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps down -v
```

## Kubernetes expansion note

Do not add `compose.prod.yml` for this repository. Production/staging should be represented by future Kubernetes resources under `infra/k8s/base` and `infra/k8s/overlays/<environment>`.
