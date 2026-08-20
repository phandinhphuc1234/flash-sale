# Manifest Model: Kubernetes Manifest Bootstrap

| Concept | Namespace / identity | Owned fields | Lifecycle |
|---|---|---|---|
| Namespace | flash-sale | labels identifying the Flash Sale system | Base resource, rendered in every overlay |
| Runtime ConfigMap | flash-sale-runtime-config | non-secret profile, HTTP port, JVM option | Created by the dev overlay |
| Workload | one Deployment per approved service | image placeholder, labels, probes, resources, env references | Defined in base, image resolved by overlay |
| Internal endpoint | one Service per workload | ClusterIP port 8080 and workload selector | Defined in base |
| External secret reference | flash-sale-secrets | name only; no fields or values are owned here | Must exist outside Git before live apply |
| Image mapping | service placeholder name | ECR repository and temporary initial tag | Owned by dev overlay until GitHub delivery updates it |

## Invariants

1. There are exactly eight workload/service pairs in this bootstrap.
2. A Service selector exactly matches only the labels of its same-named Deployment.
3. No Kubernetes Secret resource or secret value is source controlled.
4. All Services are ClusterIP.
5. All workloads expose startup, liveness, and readiness paths before any later live rollout.
