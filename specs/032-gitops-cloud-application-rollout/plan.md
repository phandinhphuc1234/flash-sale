# Implementation Plan: Cloud Application Rollout

**Branch**: `codex/gitops-phase17-cloud-application-rollout` | **Date**: 2026-08-22 | **Spec**:
[spec.md](spec.md)

**Status**: Approved

## Summary

Add a guarded Phase 17 rollout script for the existing cloud application overlay and correct the
Payment Service security composition so Actuator probes are public even when Payment acceptance is
disabled. The script validates prerequisites and Kustomize output before applying, then waits for
the eight application Deployments. No database, Kafka, Stripe, or API contract changes are made.

## Technical Context

**Language/Version**: PowerShell 7+ for the operator runner; Java 21 / Spring Boot 3.5 for Payment.

**Primary Dependencies**: kubectl, Kustomize, Maven wrapper, Spring Security, Spring Boot Actuator.

**Storage**: Existing PostgreSQL, Redis, Kafka, and ECR resources; no new storage.

**Testing**: `./mvnw -pl services/payment-service -am verify`, `./mvnw clean verify` when available,
Kustomize client dry-run, script validation, and live rollout checks.

**Target Platform**: AWS EKS `flash-sale-dev`, namespace `flash-sale`.

**Project Type**: Monorepo of independently deployable Spring Boot services plus root GitOps assets.

**Performance Goals**: Every application Deployment available within the operator timeout; no
application latency target is changed.

**Constraints**: No Secret values printed or committed; Payment acceptance and Stripe remain false;
health probes cannot require bearer authentication.

**Scale/Scope**: Eight Deployments, one replica each, one cloud namespace.

## Constitution Check

- Approved spec and tasks define the rollout and the Payment probe fix.
- No service boundary, database ownership, HTTP/Kafka contract, or dependency changes.
- Shared rollout assets stay under `infra/`; Payment security remains in its service package.
- Actuator endpoints remain declarative and use the existing runtime Prometheus registry.
- Validation includes module tests, Kustomize dry-run, prerequisite checks, and live rollout evidence.

## Design

### Payment security composition

`PaymentSecurityConfiguration` is loaded for every servlet deployment. Its Payment API chain remains
conditional on `payment.acceptance.enabled=true`; its default chain always permits only Actuator
health/info/prometheus and denies all other unmatched requests. This prevents Spring Boot's fallback
resource-server chain from protecting probes when the feature is disabled.

### Rollout runner

`infra/scripts/gitops/phase17-application-rollout.ps1` validates the namespace, platform resources,
service ConfigMaps, least-privilege Secret names, rendered Deployment count, and client dry-run.
Validation-only is the default. `-Apply` applies the existing cloud overlay and waits for each
Deployment; a failed wait stops without deleting Pods or changing Secrets.

## Project Structure

```text
services/payment-service/src/main/java/.../security/PaymentSecurityConfiguration.java
services/payment-service/src/test/java/.../security/PaymentSecurityConfigurationTests.java
infra/scripts/gitops/phase17-application-rollout.ps1
infra/scripts/gitops/README.md
specs/032-gitops-cloud-application-rollout/{spec,plan,research,data-model,quickstart,tasks,validation}.md
```

## Complexity Tracking

| Decision | Reason | Simpler alternative rejected because |
|---|---|---|
| Separate conditional Payment API chain from always-loaded probe chain | Disabled Payment still needs safe probes | Setting acceptance true would enable business persistence/API behavior accidentally |
| Guarded rollout script | Prevent partial cloud rollout from hidden prerequisites | Raw `kubectl apply` gives no consistent preflight or ordered evidence |
