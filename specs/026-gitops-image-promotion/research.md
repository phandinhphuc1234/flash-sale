# Research: Product Pilot Image Promotion

## Decision 1: Use GitHub OIDC for AWS authentication

- **Decision**: The hosted workflow assumes the existing `github-ci-role` through GitHub's OIDC
  token rather than storing an AWS access key.
- **Rationale**: Credentials are short-lived, scoped by the role trust policy, and never committed
  or placed in GitHub secrets.
- **Alternatives considered**: Long-lived access keys were rejected because they increase leak and
  rotation risk; creating a second role was rejected because the project already has an approved
  role.
- **Operational note**: The existing role currently has the AWS-managed ECR PowerUser policy. The
  workflow does not widen it; narrowing it to the eight required ECR actions is deferred to a
  security-hardening phase.

## Decision 2: Use immutable commit-derived ECR tags

- **Decision**: Tag the Product image as `pilot-<full Git SHA>` and rely on the existing immutable
  ECR repository setting.
- **Rationale**: The source commit, image, promotion PR, and Argo revision remain auditable; tags
  cannot silently change.
- **Alternatives considered**: `latest` or a mutable environment tag was rejected because it hides
  what Argo should run.

## Decision 3: Promote through a pull request

- **Decision**: The workflow pushes a branch containing only the Product pilot image tag change and
  opens a PR into protected `develop`.
- **Rationale**: Branch protection and the existing `Maven Verify` check remain effective, and Argo
  sees only reviewed Git state.
- **Alternatives considered**: Directly committing to `develop` was rejected because it bypasses
  review and can cause a deployment without the required check.

## Decision 3a: Dispatch CI for the automation branch

- **Decision**: After creating the promotion PR, dispatch the existing `ci.yml` workflow against the
  automation branch.
- **Rationale**: GitHub does not recursively start most workflows for events created with
  `GITHUB_TOKEN`; an explicit dispatch prevents the protected `Maven Verify` check from remaining
  pending.
- **Alternatives considered**: A second long-lived PAT or GitHub App token was deferred because it
  adds another credential boundary to the internship-sized flow.

## Decision 4: Keep the first delivery slice Product-only

- **Decision**: Build and promote only `product-service` and the existing `dev-pilot` overlay.
- **Rationale**: Phase 10 proves only this stateful pilot; expanding to eight services would mix
  unproven runtime configuration into the first CI/CD exercise.
- **Alternatives considered**: Full-monorepo image promotion was deferred until each service has a
  deployable image and backing-service contract.

## Decision 5: Keep repository and Kubernetes secrets operator-managed

- **Decision**: Do not add GitHub PATs, AWS credentials, Stripe values, or Kubernetes Secret data to
  the workflow or Git.
- **Rationale**: The repository is private and Argo already uses a cluster-local repository Secret.
- **Alternatives considered**: Embedding credentials in `.env`, workflow YAML, or a committed Secret
  was rejected.
