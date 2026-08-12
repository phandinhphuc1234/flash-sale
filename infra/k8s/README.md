# Kubernetes Infrastructure

This directory is reserved for first-party Kubernetes resources, including future shared bases and
environment overlays. Kubernetes Service and DNS remain the only service-discovery mechanism, and
public traffic must enter through `api-gateway`.

Docker Compose now provides a local developer topology under `infra/docker/`, but it is not the
production deployment source of truth. A future Kubernetes feature should introduce a structure like:

```text
infra/k8s/
├── base/
│   ├── api-gateway/
│   ├── authentication-service/
│   ├── product-service/
│   ├── cart-service/
│   ├── campaign-service/
│   ├── flashsale-service/
│   ├── order-service/
│   ├── payment-service/
│   ├── notification-service/
│   └── inventory-service/
└── overlays/
    ├── dev/
    ├── staging/
    └── prod/
```

Expected mapping from the local Docker setup:

| Local Docker concept | Future Kubernetes concept |
|---|---|
| `services/<service>/Dockerfile` image | `Deployment` container image |
| Compose service name | `Service` name and DNS entry |
| `.env.example` non-secret values | `ConfigMap` |
| ignored `.env` secret values | `Secret` or external secret provider |
| `/actuator/health/liveness` | `livenessProbe` |
| `/actuator/health/readiness` | `readinessProbe` |
| Liquibase execution | service-owned `Job` before app rollout |

Every changed overlay must pass:

```bash
kubectl apply --dry-run=client -k <overlay>
```

No Kubernetes resource is implemented in the current skeleton.

See [Container Compose and Kubernetes strategy](../../docs/deployment/container-compose-k8s-strategy.md)
for the local-to-cluster migration path.
