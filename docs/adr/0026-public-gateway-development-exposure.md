# ADR 0026: Development Public Gateway Exposure

**Status**: Proposed
**Date**: 2026-08-23

## Context

The AWS EKS development environment currently exposes only an internal `ClusterIP` API Gateway and
uses a localhost port-forward for Phase 22 smoke tests. The canonical roadmap next asks for a
temporary cloud endpoint while preserving the rule that every external request enters through
`api-gateway`. Backend services, PostgreSQL, Redis, Kafka, and Schema Registry must remain private.

## Decision pending

The recommended development baseline is one Kubernetes `Service` of type `LoadBalancer` for
`api-gateway`, with AWS NLB annotations and the existing public-subnet discovery tags. This is the
smallest topology change and avoids adding a second Ingress/ALB controller lifecycle. The endpoint
would use the AWS-generated hostname and remain HTTP-only for the internship environment; custom
DNS, ACM/TLS, WAF, and production hardening remain separate work.

Before implementation, the operator must confirm:

1. the existing EKS Service LoadBalancer integration is acceptable instead of installing the AWS
   Load Balancer Controller; and
2. the generated HTTP hostname is acceptable for development-only testing.

## Alternatives considered

- **AWS Load Balancer Controller + Ingress/ALB**: richer TLS/security integration, but adds CRDs,
  IAM/IRSA, Helm ownership, and another controller lifecycle.
- **NodePort/direct node IP**: rejected because it bypasses a stable managed edge.
- **Public backend Services**: rejected because it violates the single Gateway ingress boundary.

## Consequences

- Positive: one public edge, simple GitOps rollback, no application contract changes.
- Negative: HTTP-only generated hostname is not a production security posture; endpoint creation can
  incur AWS cost and may be delayed by cloud reconciliation.
- Rollback: restore the Service to `ClusterIP` through Git and verify Argo convergence; do not delete
  application data or platform state.

## Approval

- 2026-08-23 — Proposed; awaiting operator confirmation before public manifest changes.
