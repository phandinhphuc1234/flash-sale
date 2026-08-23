# Feature Specification: Development Public Gateway on AWS

**Feature Branch**: `codex/gitops-phase23-public-gateway`
**Created**: 2026-08-23
**Status**: Draft
**Input**: Continue the canonical GitOps roadmap after the authenticated internal E2E smoke and expose the API Gateway through an AWS endpoint for the cloud-only internship environment.

## Problem and Scope

The EKS environment currently proves the complete business journey only through a temporary
localhost port-forward. Operators and external clients cannot reach the single approved edge
through an AWS endpoint. This feature adds a development-only public entry point while keeping all
business services and backing services private.

### In Scope

- Expose only `api-gateway` through one AWS-managed endpoint in the `flash-sale-dev` EKS cluster.
- Preserve the existing Gateway routes, JWT validation, rate limiting, trace propagation, and
  anonymous-admin rejection.
- Keep authentication, Product, Campaign, Flash Sale, Inventory, Order, Payment, PostgreSQL,
  Redis, Kafka, and Schema Registry Services internal.
- Provide a bounded operator smoke that discovers the AWS endpoint, checks readiness and catalog
  access, and verifies the protected admin boundary.
- Provide a GitOps rollback to the current internal `ClusterIP` state and record the endpoint and
  cleanup outcome without printing secrets or tokens.

### Out of Scope

- A production environment, custom domain, DNS zone, WAF, autoscaling, or multi-region topology.
- Stripe/Payment enablement, database changes, Kafka contract changes, or service source changes.
- Public access to any service other than `api-gateway`.
- Treating the generated AWS hostname as a permanent product URL.

## User Scenarios & Testing

### User Story 1 - Reach the approved edge from AWS (Priority: P1)

As a cloud operator, I want one AWS endpoint for the API Gateway, so that I can run the existing
Gateway smoke without a local port-forward.

**Why this priority**: It is the next canonical GitOps milestone and proves the cloud edge path.

**Independent Test**: After reconciliation, discover the Gateway Service external hostname and
receive readiness `200`, catalog `200`, and anonymous admin `401` over the endpoint.

**Acceptance Scenarios**:

1. **Given** the cloud overlay is healthy and the public edge is enabled, **When** an operator calls
   the generated AWS endpoint, **Then** only the API Gateway responds and its readiness endpoint is
   `200`.
2. **Given** a public catalog request, **When** it passes through the AWS endpoint, **Then** the
   existing Gateway route returns `200` without exposing a backend Service.
3. **Given** an unauthenticated admin request, **When** it passes through the AWS endpoint, **Then**
   the response remains `401` or `403` and no admin data is returned.

### User Story 2 - Reconcile and roll back safely (Priority: P1)

As an operator, I want public exposure to be Git-managed and reversible, so that a failed edge
change can return to the internal `ClusterIP` state without touching application data.

**Independent Test**: Merge the approved overlay change, verify Argo `Synced/Healthy`, then merge
the rollback change and verify the external endpoint disappears and the Service is `ClusterIP`.

**Acceptance Scenarios**:

1. **Given** a public-edge commit is merged, **When** Argo reconciles, **Then** the Gateway Service
   reaches `LoadBalancer` and reports an AWS hostname within the bounded timeout.
2. **Given** the public smoke fails or the operator requests rollback, **When** the rollback commit
   is reconciled, **Then** the Gateway Service is `ClusterIP` and no backend or platform Service is
   public.

### Edge Cases

- The AWS endpoint remains pending: the runner stops with diagnostics and does not mutate unrelated
  resources.
- The endpoint resolves but readiness is not `200`: the runner reports the stage and leaves cleanup
  to the Git revert/rollback path.
- A public Service is detected for any non-Gateway component: the guard fails immediately.
- The cloud overlay is not `Synced/Healthy`: the runner refuses to test the endpoint.

## Requirements

### Functional Requirements

- **FR-001**: The cloud desired state MUST expose exactly one public Service, owned by `api-gateway`.
- **FR-002**: All other application and platform Services MUST remain `ClusterIP`; no Ingress or
  direct backend route may be introduced.
- **FR-003**: The public endpoint MUST preserve the existing Gateway HTTP routes and authorization
  behavior without changing application contracts.
- **FR-004**: The verification helper MUST discover the endpoint from Kubernetes, use bounded DNS and
  HTTP waits, and never print bearer tokens, passwords, cookies, Secret values, or `.env` values.
- **FR-005**: The helper MUST verify Argo `Synced/Healthy`, Gateway readiness `200`, catalog `200`,
  and anonymous admin `401`/`403` before reporting success.
- **FR-006**: A rollback MUST restore the Gateway Service to `ClusterIP` through GitOps and MUST NOT
  delete application data, PVCs, Secrets, or Kafka topics.
- **FR-007**: The generated AWS hostname MUST be documented as development-only and MUST NOT be used
  as a permanent domain.

### Non-Functional Requirements

- **NFR-SEC-001**: This is a development-only HTTP endpoint; no production credentials or Stripe
  flags may be enabled by this feature.
- **NFR-OPS-001**: Endpoint discovery and smoke verification MUST terminate within 600 seconds and
  clean up temporary local processes/files on every exit path.
- **NFR-REL-001**: A failed public-edge rollout MUST leave all backend Services private and provide a
  deterministic Git revert path.

## Key Entities

- **Public Gateway Service**: The sole Kubernetes edge Service whose AWS-assigned hostname is the
  temporary development entry point.
- **Gateway Smoke Result**: Sanitized record of Argo state, endpoint, HTTP statuses, rollout timing,
  and rollback/cleanup outcome.

## Success Criteria

### Measurable Outcomes

- **SC-001**: One and only one public Service is present in namespace `flash-sale` after reconciliation.
- **SC-002**: The public smoke obtains readiness `200`, catalog `200`, and anonymous admin `401`/`403`
  in one bounded run of at most 600 seconds.
- **SC-003**: A rollback returns the Gateway Service to `ClusterIP` and leaves all other Services
  private in one reconciliation cycle.
- **SC-004**: Validation evidence contains no password, token, cookie, Kubernetes Secret value, or
  `.env` value.

## Assumptions

- The environment remains the single AWS EKS development environment; there is no product/staging
  environment.
- The EKS VPC already tags public subnets for AWS load-balancer discovery.
- The generated AWS hostname is sufficient for this internship smoke; custom DNS/TLS is deferred to
  a separately approved feature.
- The selected AWS load-balancer integration is available in the existing EKS foundation; if the
  cluster requires an additional controller or IAM role, that dependency must be added to the plan
  and approved before implementation.

## Human Decisions Required

- **[NEEDS CLARIFICATION: Confirm the development exposure mechanism before implementation: use the existing EKS Service LoadBalancer integration with an AWS-managed NLB, or install/manage AWS Load Balancer Controller and use an Ingress/ALB? The former is smaller and avoids a new controller; the latter offers richer routing and TLS but adds IAM/controller lifecycle.]**

- **[NEEDS CLARIFICATION: Confirm that the temporary development endpoint may be HTTP-only with the AWS-generated hostname. HTTPS/custom domain requires an ACM certificate and DNS ownership and is intentionally not included in this phase.]**

## Constitutional Constraints

- **Service ownership**: Only root `infra/k8s` and GitOps scripts change; no service database or source changes.
- **External ingress**: All external traffic enters through `api-gateway`; backend and platform Services remain internal.
- **API/event contracts**: Existing Gateway contracts are reused; no HTTP/Kafka contract changes.
- **Durable and hot-path data**: No PostgreSQL, Redis, or Kafka data behavior changes.
- **Messaging reliability**: No consumer, outbox, retry, or schema behavior changes.
- **Root infrastructure ownership**: Kubernetes exposure and verification assets remain under `infra/`; an ingress ADR is required.
- **Observability**: Existing readiness/Prometheus configuration is reused; evidence records endpoint health and trace-safe status only.
- **Verification**: Kustomize dry-run, public Service inventory, Argo health, endpoint smoke, and rollback verification are required.
- **Architecture decisions**: `docs/adr/0026-public-gateway-development-exposure.md` must be Accepted before production manifest changes.

## Approval and History

- 2026-08-23 — Draft created after the successful Phase 22 internal E2E run.
