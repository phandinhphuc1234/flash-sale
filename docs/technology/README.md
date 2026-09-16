# Technology Documentation

This section explains which engineering problem each technology solves. Runtime truth still comes
from source/configuration, and business behavior comes from approved feature artifacts.

## Current stack by problem

| Problem | Technology/approach |
|---|---|
| Independently packaged services | Java 21, Maven reactor, Spring Boot 3.5 |
| Public edge | Spring Cloud Gateway WebFlux |
| Synchronous internal decisions | Spring Cloud OpenFeign + OAuth2 client credentials |
| Durable relational ownership | PostgreSQL, Spring Data JPA, Liquibase |
| Seckill contention | Redis Lua and Redis Stream handoff |
| Durable asynchronous workflows | Kafka, Avro SpecificRecord, Schema Registry, outbox/inbox |
| Authentication | Spring Security, RS256 JWT/JWKS, refresh rotation |
| Payment provider | Stripe Checkout and signed webhooks |
| Deployment | Docker, Kustomize, Terraform, EKS, Argo CD, GitHub Actions OIDC |
| Application observability | Actuator/Micrometer, Prometheus, Grafana |

Start here:

- [`technology-problem-map.md`](technology-problem-map.md)
- [Service communication protocols](../architecture/service-communication-protocols.md)
- [Liquibase migration rules](liquibase-migration-rules.md)
- [Gateway rate-limiting design](../ratelimit/README.md)
- [Architecture diagrams](../architecture/diagrams/README.md)

## Status language

- **Implemented** — production source/config exists.
- **Configured** — desired state exists but needs environment-specific verification.
- **Candidate/deferred** — design discussion only; no runtime promise.

OpenTelemetry collectors, Loki, Tempo, gRPC, Alertmanager/Slack, HA monitoring, infrastructure
exporters, and Notification delivery remain deferred. Kubernetes/Argo/Prometheus/Grafana desired
state exists, but the EKS cluster is currently absent after cost-control cleanup.

See the [deployment strategy](../deployment/container-compose-k8s-strategy.md) for local/cloud
ownership and the [current implementation](../current-system-implementation.md) for status.
