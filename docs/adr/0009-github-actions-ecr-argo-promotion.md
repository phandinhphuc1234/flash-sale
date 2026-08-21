# ADR 0009: Reviewable Product Image Promotion from GitHub Actions to Argo CD

**Status**: Accepted
**Date**: 2026-08-21

## Context

Phase 10 made the `dev-pilot` overlay the Argo CD source of truth, but a new application image still
requires manual build, ECR push, and image-tag editing. The repository protects `develop`, and the
GitHub repository is private.

## Decision

Create a Product-only GitHub Actions delivery workflow. After verification, it assumes the existing
GitHub OIDC role, pushes an immutable commit-derived image to ECR, and opens a pull request containing
only the `dev-pilot` image tag change. Argo CD reconciles only after that PR is reviewed and merged.

The local PowerShell helper follows the same boundaries, defaults to validation, and never commits or
pushes Git changes. Credentials remain outside Git and Kubernetes Secrets remain operator-managed.
The existing role currently uses the AWS-managed ECR PowerUser policy; narrowing that policy is a
separate security-hardening change and is not silently performed by this phase.

## Alternatives considered

- Direct push to `develop`: rejected because it bypasses branch protection and review.
- Mutable `latest` image: rejected because the deployed artifact would not be traceable.
- Long-lived AWS access key: rejected because GitHub OIDC is already available.
- Full eight-service rollout: deferred until the remaining service deployment slices are proven.

## Consequences

Positive:

- Source commit → immutable ECR image → reviewed PR → Argo revision is auditable.
- Failed verification or publish stops before desired-state mutation.
- No new Kubernetes controller or application dependency is introduced.

Trade-offs:

- Every image promotion requires a PR review.
- The existing IAM role trust and GitHub workflow permissions must be maintained.
- The first slice is intentionally Product-only.

## Rollback

Close or revert the promotion PR. Argo CD will keep or restore the previous image tag from Git. ECR
images are retained because deletion policy is outside this phase.
