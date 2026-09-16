# Technology Problem Map

This map explains why a technology exists and prevents tools from leaking into business ownership.

| Problem | Current choice | Owner location | Guardrail |
|---|---|---|---|
| Java service runtime | Java 21, Spring Boot 3.5 | `services/*`, root `pom.xml` | Independently packaged applications |
| Dependency/build consistency | Maven Wrapper/reactor | root `.mvn/`, `pom.xml` | No service-local wrappers |
| Public HTTP edge | Spring Cloud Gateway WebFlux | `services/api-gateway` | No business data/workflows in Gateway |
| Internal immediate decisions | OpenFeign over HTTP | outbound adapters in consumer service | Approved contract, OAuth2 service JWT, bounded timeouts |
| Human/service identity | Spring Security, RS256 JWT/JWKS, client credentials | Authentication + each resource server | Validate issuer/audience/type/subject/scope |
| Durable service state | PostgreSQL + Spring Data JPA | owning service | No cross-service DB access |
| Schema evolution | Liquibase one-off process/Job | service changelog + root orchestration | App replicas do not race migrations |
| Seckill atomic decision | Redis Lua | Flash Sale adapter | Redis is not durable business truth |
| Crash-safe Redis handoff | Redis Stream | Flash Sale adapter | Persist PostgreSQL journal/outbox before ack |
| Durable async workflows | Kafka | service Kafka adapters | Versioned contracts and idempotent consumers |
| Event schema/evolution | Avro SpecificRecord + Schema Registry | `contracts/kafka-avro-contracts` | Controlled compatibility/registration |
| State plus required event | Transactional outbox | service-owned database/adapters | Local transaction, leased relay, bounded retry |
| Multi-service business process | Order-owned Saga | Order domain/application | No global Saga service; owners keep their rules |
| Payment provider | Stripe Checkout/webhooks | Payment adapters | Idempotency, signature verification, receipt dedup |
| Local topology | Docker Compose | `infra/docker` | No Compose production topology |
| Cloud foundation/runtime | Terraform + EKS + Kustomize | `infra/terraform`, `infra/k8s` | Review plan; private nodes; Git desired state |
| Continuous delivery | GitHub Actions OIDC + ECR + Argo CD | `.github/workflows`, `infra/k8s/argocd` | Human-reviewed promotion PR |
| Metrics/health | Actuator/Micrometer + Prometheus/Grafana | service config + `infra/monitoring` | Low-cardinality labels; no custom registry bean |
| Feature governance | Spec Kit artifacts | `.specify`, `specs` | WHAT -> HOW -> tasks -> evidence |

## Why HTTP and Kafka both exist

Use HTTP when the caller cannot continue without an immediate authoritative decision, such as a
Product quote or Inventory hold. Use Kafka for durable cross-service commands/facts, fan-out, and
workflow steps that need independent retry/recovery. Do not replace every call with events, and do
not build a distributed transaction from chained synchronous calls.

## Why Redis and PostgreSQL both exist

Redis Lua serializes the tiny Flash Sale contention decision and provides predictable atomic quota/
idempotency behavior. PostgreSQL owns the durable reservation journal and business history. The
Redis Stream connects the two safely after Lua acceptance.

## Why an outbox exists

A database commit followed by a direct Kafka send has a crash window: state can commit while the
event is lost. The outbox inserts the event beside the state in one local transaction; a separate
relay publishes it idempotently and records progress.

## Why GitOps uses two pull requests

The source PR approves code. CI then creates immutable ECR images and a second PR that approves the
exact image tags in Kustomize. Argo CD deploys only after that desired-state PR is merged. This
separates “code passed” from “this artifact may run in cloud” and makes deployment history auditable.

## Deliberately deferred

- Notification email/SMS/push domain and provider integration;
- OpenTelemetry collector, Tempo, Loki, and centralized log storage;
- Alertmanager/Slack/PagerDuty notification routing;
- Kafka/Redis/PostgreSQL/node exporters and HA monitoring;
- gRPC, service mesh, Eureka, and a global Saga/orchestrator service;
- a separate production environment.

The absence of these tools is intentional for the current internship-scale scope. Introduce one
only when an approved plan names the problem, ownership, failure model, operational cost, and tests.
