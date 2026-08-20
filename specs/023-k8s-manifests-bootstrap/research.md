# Research: Kubernetes Manifest Bootstrap

## Decision 1: Kustomize base plus dev overlay

**Decision**: Put reusable service manifests in infra/k8s/base and development-only image/configuration
values in infra/k8s/overlays/dev.

**Why**: The base remains environment-neutral and Argo CD can later consume the overlay as the one
desired-state entry point.

**Alternatives rejected**:
- One large environment-specific YAML file: difficult to reuse and review.
- Helm: adds chart ownership and templating complexity before a single overlay has been proven.
- Manifests inside each service: violates shared platform asset ownership.

## Decision 2: Source-only, client-side validation

**Decision**: Render and client dry-run only. Do not apply workloads to EKS in this feature.

**Why**: Existing Compose service names and credentials cannot make a Pod in EKS reach PostgreSQL,
Redis, Kafka, or Schema Registry. A running-but-unready Pod would not prove GitOps readiness.

**Alternatives rejected**:
- Apply Deployments now: creates misleading CrashLoopBackOff resources.
- Supply guessed environment values: would invent a data topology and leak credentials.

## Decision 3: Reference but do not create secrets

**Decision**: Deployments name a namespace-scoped flash-sale-secrets object via envFrom, but no
Secret manifest or value enters Git.

**Why**: Keeps the Kubernetes structure visible while preserving secret handling outside source
control.

**Alternatives rejected**:
- Commit base64 values: base64 is not encryption.
- Omit all secret references: hides an operational prerequisite from reviewers.

## Decision 4: Internal-only Services

**Decision**: Every service, including api-gateway during this bootstrap, uses ClusterIP.

**Why**: It prevents accidental public cloud exposure. The future public entry point must be
api-gateway, but choosing ALB/NLB, DNS, TLS, and network policy needs a separate approved design.

## Decision 5: Placeholder image tag

**Decision**: Use existing ECR repository paths and tag initial only for rendering.

**Why**: Current ECR repositories exist, but no immutable service image is approved/deployed by this
feature. The later delivery workflow will update images to SHA-derived immutable tags.
