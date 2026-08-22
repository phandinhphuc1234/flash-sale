# Feature Specification: Cloud Gateway Smoke

**Feature Branch**: codex/gitops-phase18-gateway-smoke
**Created**: 2026-08-22
**Status**: Approved

## Problem and Scope

Phase 17 proves that the application Deployments are available, but the API Gateway remains an
internal ClusterIP Service. The next safe verification is an operator-run smoke test through a
temporary local port-forward. This proves the gateway process, route table, service discovery, and
basic authorization boundaries without introducing an ingress, DNS name, TLS certificate, or a
production environment.

### In scope

- Validate the canonical cloud Kustomize overlay and the ready API Gateway Deployment/Service.
- Port-forward service/api-gateway to a developer-selected local port for one smoke run.
- Check Gateway readiness, the public catalog route, and a protected admin route.
- Stop the temporary port-forward in all exit paths and avoid printing Secret values.

### Out of scope

- Public LoadBalancer or Ingress exposure, DNS, TLS, or WAF.
- Enabling Payment, Stripe, Kafka consumers, recovery workers, or new credentials.
- Changes to service APIs, Kafka contracts, databases, or application business code.

## User Story and Testing

As an operator, I want to exercise the cloud application through the API Gateway without making it
public, so that I can detect broken routing or authorization before adding an external edge.

Independent test: run the script in validation mode, then with -Run; Gateway readiness and the
public catalog route return HTTP 200, while an unauthenticated admin route returns HTTP 401 or 403.

## Requirements

- FR-001: Validate the cloud overlay with kubectl apply --dry-run=client -k before a smoke run.
- FR-002: Require the flash-sale namespace, api-gateway Service, and a Ready api-gateway Deployment.
- FR-003: Use a temporary local port-forward and do not change the Service type or create an
  external endpoint.
- FR-004: Verify Gateway readiness, public catalog success, and protected admin behavior using
  bounded HTTP calls.
- FR-005: Stop the port-forward in cleanup and never read or print Kubernetes Secret values.

## Success Criteria

- SC-001: Validation mode passes with no live resource changes.
- SC-002: A smoke run reports readiness 200, catalog 200, and admin 401 or 403.
- SC-003: No public cloud endpoint, DNS, TLS, or new secret is introduced.
