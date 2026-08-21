# Research: Product Pilot GitOps Rollback Rehearsal

## Decision 1: Revert Git desired state, not the cluster directly

- **Decision**: Use `git revert -m 1` on the merged promotion commit, open a PR, and let Argo CD
  reconcile after merge.
- **Rationale**: Git remains the auditable source of truth, branch protection remains effective, and
  the rollback can be reviewed and repeated.
- **Alternatives considered**: `kubectl set image` or `kubectl apply` would create drift and bypass
  Git review; both are rejected.

## Decision 2: Retain ECR images

- **Decision**: Verify that the previous tag exists and retain both image artifacts.
- **Rationale**: Rollback needs the old image, and ADR 0009 explicitly keeps deletion policy outside
  this phase.
- **Alternatives considered**: Deleting the newer image is destructive and breaks auditability.

## Decision 3: Restore the forward state

- **Decision**: After rollback evidence, create a second reviewed revert to return to the original
  promoted tag.
- **Rationale**: The shared development pilot must not remain on an obsolete image after the rehearsal.
- **Alternatives considered**: Leaving the rollback live would make the test environment diverge from
  the latest approved promotion.
