# Research: Cloud Gateway Smoke

## Decision

The cloud overlay intentionally keeps api-gateway internal. A temporary kubectl port-forward
provides a deterministic operator smoke path without creating a public endpoint, DNS, TLS, or a
new AWS resource. Existing routes cover the public catalog and protected admin boundaries.

## Alternatives rejected

- Service type LoadBalancer: expands cloud exposure and cost before ingress/TLS decisions.
- Ingress/ALB: requires DNS, certificates, trusted proxy headers, and an explicit security plan.
- Direct calls to Product Service: bypass the component whose routing and edge authorization this
  phase verifies.
