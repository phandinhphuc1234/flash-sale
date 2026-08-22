# Data Model: Cloud Application Rollout

This phase introduces no durable data model or schema. The rollout operates on existing Kubernetes
resources:

| Resource group | Objects | Ownership |
|---|---|---|
| Application | 8 Deployments and 8 Services | Each application service; API Gateway is stateless |
| Configuration | Shared and service ConfigMaps | Root `infra/k8s` deployment configuration |
| Secrets | Service-specific Secret boundaries and `auth-jwt` | Operator-provided values; never committed |
| Platform | PostgreSQL, Redis, Kafka, Schema Registry | Root cloud platform overlay |
