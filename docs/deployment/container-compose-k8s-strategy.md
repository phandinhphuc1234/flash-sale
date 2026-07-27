# Container Compose and Kubernetes Strategy

This document defines how Flash Sale should use Docker, Docker Compose, and future Kubernetes
resources without mixing their responsibilities.

## Short answer

- Each service has its own `Dockerfile`.
- Shared local orchestration lives in `infra/docker/compose.yml`.
- Local debug-only overrides live in `infra/docker/compose.dev.yml`.
- Do not add `compose.prod.yml`; production/staging should be Kubernetes.
- Future Kubernetes resources should live in `infra/k8s/base` and `infra/k8s/overlays/<env>`.

## Why no `compose.prod.yml`?

Docker Compose is excellent for local development and integration smoke tests. Kubernetes is the
repository's target runtime platform. Adding `compose.prod.yml` would create a second production
topology that can drift from Kubernetes manifests.

Docker documents multiple Compose files as a way to customize applications for different
environments or workflows, while warning that complexity moves into infrastructure and configuration
as the number of Compose files grows. For this repo, the clean split is:

| Need | File/location | Reason |
|---|---|---|
| Local baseline | `infra/docker/compose.yml` | One shared developer topology |
| Local debug ports | `infra/docker/compose.dev.yml` | Optional and clearly non-production |
| Production/staging/dev cluster | `infra/k8s/overlays/<env>` | Kubernetes is the source of truth |

## Image ownership

Every service owns a service-local image recipe:

```text
services/<service>/Dockerfile
```

Build from the repository root:

```powershell
docker build -f services/order-service/Dockerfile -t flash-sale/order-service:local .
```

The Dockerfiles use multi-stage builds:

1. JDK build stage runs Maven for the owning module.
2. JRE runtime stage contains only the runnable jar and runs as a non-root user.

This follows Docker's multi-stage build guidance: build tools and intermediate artifacts stay out
of the final runtime image.

## Compose topology

Default local topology:

```text
host
  ↓ localhost:8080
api-gateway
  ↓ Docker DNS names
authentication-service, product-service, cart-service, campaign-service, flashsale-service,
order-service, payment-service, notification-service, inventory-service
  ↓
postgres, redis, kafka
```

Only `api-gateway` is exposed by default. Other application services are reachable by Docker DNS
inside the Compose network:

```text
http://order-service:8080
http://payment-service:8080
http://product-service:8080
```

This mirrors future Kubernetes Service names and avoids the common beginner trap of using
`localhost` for container-to-container calls.

## Compose profiles and overrides

`infra/docker/compose.yml` has:

- default platform services: `postgres`, `redis`, `kafka`
- `apps` profile: the 10 Spring Boot service containers

Run platform only:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml up -d postgres redis kafka
```

Run all app containers:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps up --build
```

Run all app containers with direct debug ports:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps up --build
```

Use direct debug ports only for local inspection. The default ingress path remains `api-gateway`.

## Environment variables

Committed examples live in:

```text
infra/docker/.env.example
```

Real local values live in ignored files:

```text
infra/docker/.env
```

Classify variables like this:

| Variable type | Local source | Future Kubernetes source |
|---|---|---|
| Non-secret config such as host names, ports, profiles | `.env.example` / `.env` | ConfigMap |
| Secrets such as passwords, JWT signing keys, API tokens | ignored `.env` only | Secret or external secret provider |

Kubernetes ConfigMaps are intended for non-confidential key-value configuration. Kubernetes Secrets
are intended for sensitive values such as passwords, tokens, and keys.

## Database and migration rule

Local Compose provisions one PostgreSQL container and multiple logical databases:

```text
auth_db
product_db
cart_db
campaign_db
flashsale_db
order_db
payment_db
notification_db
inventory_db
```

This is a local convenience. It does not mean the system has one shared service database.

Business schema migrations still belong to the owning service:

```text
services/<service>/src/main/resources/db/changelog/
```

Current app containers set:

```text
SPRING_LIQUIBASE_ENABLED=false
```

because migrations are explicit deployment operations rather than work performed by every replica.
`product-service` is the first service with JDBC/PostgreSQL runtime and an approved business
changeset. Local development applies it through a one-off Product container; other services retain
empty changelogs until their own approved schema features.

For Kubernetes production, prefer this shape:

```text
Kubernetes Job:       runs Liquibase for one service schema
Kubernetes Deployment: runs service replicas with app-time migration disabled
```

This avoids multiple app replicas racing at startup and keeps migration execution observable.

The future Product migration Job must use the same Product image and changelog as the Deployment,
receive Product database credentials from a Secret, set `SPRING_LIQUIBASE_ENABLED=true`, run as a non-web
process, and complete successfully before Product replicas are rolled out.

## Health and probes

Every service already exposes:

```text
/actuator/health
/actuator/health/liveness
/actuator/health/readiness
/actuator/info
/actuator/prometheus
```

Future Kubernetes probes should map to:

| Kubernetes probe | Spring Boot endpoint |
|---|---|
| `livenessProbe` | `/actuator/health/liveness` |
| `readinessProbe` | `/actuator/health/readiness` |

Kubernetes uses liveness probes to decide when to restart containers and readiness probes to decide
whether a pod should receive traffic.

## Future Kubernetes structure

When Kubernetes is implemented, use Kustomize-style bases and overlays:

```text
infra/k8s/
├── base/
│   ├── api-gateway/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   └── kustomization.yaml
│   ├── cart-service/
│   ├── order-service/
│   └── ...
└── overlays/
    ├── dev/
    ├── staging/
    └── prod/
```

Each service should generally map to:

```text
Docker image        -> Deployment container image
Compose service     -> Kubernetes Service
Non-secret env vars -> ConfigMap
Secrets             -> Secret or external secret provider
Health endpoints    -> livenessProbe/readinessProbe
Liquibase execution -> Job
Public entry        -> Ingress or Gateway API route to api-gateway
```

Every changed overlay must pass:

```bash
kubectl apply --dry-run=client -k infra/k8s/overlays/<environment>
```

## Recommended development flow

Most days:

1. Start platform only with Compose.
2. Run the service you are editing from the IDE or Maven.
3. Use Docker Compose full app mode only for integration smoke tests.
4. Use Kubernetes only when testing deployment manifests or cluster behavior.

This keeps feedback fast while preserving the same naming and runtime boundaries that Kubernetes
will use later.

## References

- Docker Compose multiple files: https://docs.docker.com/compose/how-tos/multiple-compose-files/
- Docker Compose profiles: https://docs.docker.com/compose/how-tos/profiles/
- Docker multi-stage builds: https://docs.docker.com/build/building/multi-stage/
- Docker build best practices: https://docs.docker.com/build/building/best-practices/
- Kubernetes Services: https://kubernetes.io/docs/concepts/services-networking/service/
- Kubernetes ConfigMaps: https://kubernetes.io/docs/concepts/configuration/configmap/
- Kubernetes Secrets: https://kubernetes.io/docs/concepts/configuration/secret/
- Kubernetes probes: https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
