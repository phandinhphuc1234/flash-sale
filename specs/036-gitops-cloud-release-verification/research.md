# Research: Cloud Release Artifact Verification

## Decision 1: Use the existing ECR cloud artifact source

**Decision**: Resolve image tags from the cloud Deployments against the corresponding ECR repository
and compare the ECR manifest digest with the running Pod image ID.

**Rationale**: Terraform exposes ECR repositories and the cloud Kustomize overlay already references
ECR. Introducing GHCR would create a second registry and require new credentials and deployment
ownership without helping the local-plus-cloud internship environment.

**Alternatives considered**:

- GHCR release images: rejected because the deployed cloud source of truth is ECR.
- Rebuild images during verification: rejected because verification must test the artifact already
  selected by GitOps, not a new build.

## Decision 2: Compare both desired and running identities

**Decision**: Read the Deployment image reference, resolve its ECR tag to a digest, then compare that
digest with the selected Pod's `imageID`.

**Rationale**: Argo/Kubernetes can be healthy while a Pod runs an unexpected image. Comparing only tags
would not prove the binary identity.

**Alternatives considered**:

- Check only Deployment availability: rejected because health does not prove artifact identity.
- Require a new digest-pinned manifest in this phase: deferred to the release-build/promotion phase;
  this verifier records the current tag and digest without changing desired state.

## Decision 3: Reuse the existing Gateway smoke

**Decision**: Invoke the Phase 18 smoke helper with its localhost-only port-forward.

**Rationale**: The smoke contract already proves readiness 200, public catalog 200, and anonymous admin
401 without exposing a public AWS endpoint.

**Alternatives considered**:

- Add an internet-facing LoadBalancer or Ingress: rejected because the user selected cloud-only
  deployment without public production exposure.
- Duplicate the HTTP smoke logic: rejected to avoid two sources of truth.

## Decision 4: No release mutation in Phase 21

**Decision**: Phase 21 is a validation-only gate. It does not build, push, update Kustomize, reconcile,
or enable Payment.

**Rationale**: Release creation, deployment, rollback, and performance are separate roadmap phases.
Keeping this gate read-only makes failures safe to investigate and rerun.
