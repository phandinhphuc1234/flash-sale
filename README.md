# Flash Sale

Java 21 and Spring Boot 3.x microservice monorepo for a flash-sale platform. The current baseline is
an application skeleton: it defines ten independently buildable services—one API gateway and nine
business services—plus local Docker Compose orchestration and documented infrastructure boundaries,
without implementing business logic or Kubernetes deployment manifests.

## Repository layout

```text
flash-sale/
├── services/                 # Independently deployable Spring Boot applications
│   ├── api-gateway/
│   ├── authentication-service/
│   ├── product-service/
│   ├── cart-service/
│   ├── campaign-service/
│   ├── flashsale-service/
│   ├── order-service/
│   ├── payment-service/
│   ├── notification-service/
│   └── chatting-service/
├── infra/                    # Shared platform and environment assets
│   ├── docker/
│   ├── k8s/
│   ├── helm/
│   └── monitoring/
├── contracts/                # Repository-level shared contract catalog
├── docs/                     # Architecture, ADR, operations, database, and testing guidance
├── libs/                     # Approved cross-cutting libraries; never shared business ownership
├── load-tests/               # Cross-service load-test assets
├── scripts/                  # Repository automation
├── specs/                    # Spec Kit feature artifacts
└── pom.xml                   # Root Maven parent and reactor aggregator
```

The Docker baseline under `infra/docker/` is for local development and integration smoke tests.
Kubernetes, Helm, Prometheus server, and Grafana infrastructure remain planned future work.

## Ownership boundary

Service modules own their source code, POM dependencies, `application*.yml`, tests, and database
migrations. Shared local orchestration, Kubernetes deployment topology, Helm assets, Prometheus
scrape infrastructure, Grafana dashboards, and alert rules belong under root `infra/`.

This distinction matters for observability: each service keeps Actuator and the runtime
`micrometer-registry-prometheus` dependency because `/actuator/prometheus` is a runtime capability
of that service. The Prometheus server that scrapes the endpoint and the Grafana resources that
visualize it belong in `infra/monitoring/`.

See [ADR 0001](docs/adr/0001-root-infrastructure-ownership.md) for the full decision.

See [ADR 0002](docs/adr/0002-cart-service-boundary.md) for the accepted Cart service boundary and
the behavior, routes, contracts, and persistence work deliberately deferred beyond its current shell.

See [Service Clean/Hexagonal structure](docs/architecture/service-clean-hex-structure.md) for the
canonical service package baseline, dependency direction, create-on-demand rule, and practical
placement of DTOs, mappers, exceptions, persistence types, integrations, configuration, and tests.

See [Service communication protocols](docs/architecture/service-communication-protocols.md) for
the approved HTTP/Kafka baseline, JWT validation boundary, deferred gRPC rule, and flash-sale
reliability guardrails.

## Build

Requirements: Java 21 and network access for the first Maven Wrapper dependency download.

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -pl services/product-service -am verify
```

```bash
./mvnw clean verify
./mvnw -pl services/product-service -am verify
```

## Local Docker runtime

Prepare a local environment file:

```powershell
Copy-Item infra/docker/.env.example infra/docker/.env
```

Validate Compose:

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml config
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps config
```

Run platform services only:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml up -d postgres redis kafka
```

Run the full local app topology:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps up --build
```

By default, only `api-gateway` is exposed on `localhost:8080`. Use
`infra/docker/compose.dev.yml` only when you want direct host ports for non-gateway service
debugging.

See [Container Compose and Kubernetes strategy](docs/deployment/container-compose-k8s-strategy.md)
for the Compose-to-Kubernetes path and why this repository does not use `compose.prod.yml`.

## Operational endpoints

Every service declaratively exposes these Spring Boot Actuator endpoints on its application port:

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/info`
- `/actuator/prometheus`

Spring Boot 3.x discovers the Prometheus registry from the runtime classpath; no custom registry
bean is used. Production reachability, scrape discovery, and access control belong to a later
infrastructure feature.
