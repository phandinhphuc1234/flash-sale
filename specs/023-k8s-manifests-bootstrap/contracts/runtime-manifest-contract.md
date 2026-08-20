# Runtime Manifest Contract

## Scope

This is an infrastructure contract for the Phase 7 source baseline. It is not an HTTP or Kafka
contract and it creates no live resources.

## Rendered dev overlay

The overlay must render:

| Kind | Name rule | Count |
|---|---|---:|
| Namespace | flash-sale | 1 |
| ConfigMap | flash-sale-runtime-config | 1 |
| Deployment | one per approved service | 8 |
| Service | one per approved service | 8 |

Approved service names are api-gateway, authentication-service, campaign-service,
flash-sale-service, inventory-service, order-service, payment-service, and product-service.

## Workload rules

- container port: 8080, named http;
- startup and liveness health path: /actuator/health/liveness;
- readiness health path: /actuator/health/readiness;
- Service type: ClusterIP;
- image starts as the service short-name placeholder in base;
- dev overlay maps it to the corresponding existing ECR repository with tag initial;
- envFrom includes the runtime ConfigMap and the name-only flash-sale-secrets reference.

## Compatibility and rollout

This contract has no live consumers yet. Future GitHub delivery automation may update only an
overlay image tag after an image is pushed to ECR. A backing-service and secret-management feature
must be approved before the rendered resources are applied without dry-run.
