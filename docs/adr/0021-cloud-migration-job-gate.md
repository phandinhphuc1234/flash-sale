# ADR 0021: Cloud Migration Job Gate

**Status**: Accepted
**Date**: 2026-08-21

## Context

Phase 15 leaves Liquibase disabled so application rollout cannot race database initialization. The
repository has eight independent service schemas and no service may access another service's database.
Running all migration logic inside a shared superuser Job would violate ownership and make failures
hard to attribute.

## Decision

Use a separate `infra/k8s/overlays/cloud-migrations` Kustomize overlay with one Kubernetes Job per
database-owning service. The overlay contains seven Jobs; API Gateway is stateless and has no
database migration. Each Job runs the owning service image with its own datasource URL and Secret, enables
Liquibase only for the Job, and disables listeners, schedulers, consumers, and outbox publishers.
An operator-only PowerShell script validates prerequisites by default and applies/waits for Jobs only
with explicit `-Apply`. Existing Jobs are preserved unless `-ForceRerun` is supplied.

## Alternatives

- Add migration Jobs to the application overlay: rejected because every Argo/app sync could start a
  migration concurrently with Deployments.
- Run one privileged migration container: rejected because it breaks service schema ownership.
- Run migrations from CI: rejected because CI should not receive cluster mutation authority in this
  internship flow.

## Consequences

The operator gets clear per-service completion and failure evidence. The overlay has duplicated image
and configuration references that must be updated when image promotion changes; this is an intentional
review boundary until a later release workflow centralizes immutable image inputs.
