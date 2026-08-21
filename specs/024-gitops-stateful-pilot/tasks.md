# Tasks: GitOps Stateful Product Pilot

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)
**Status**: Approved

## Phase 1: Setup and adoption inventory

- [x] T001 Confirm `SPECIFY_FEATURE_DIRECTORY` points to `specs/024-gitops-stateful-pilot` and record the existing EBS role, add-on, PVC, and Product image identities in `specs/024-gitops-stateful-pilot/quickstart.md`.
- [x] T002 [P] Add the accepted stateful pilot decision and rollback boundary to `docs/adr/0007-eks-stateful-product-pilot.md`.
- [x] T003 [P] Document Terraform and Kubernetes identity contracts in `specs/024-gitops-stateful-pilot/contracts/terraform-and-pilot.md`.

## Phase 2: Terraform EBS CSI ownership (US1)

- [x] T004 [US1] Add the EBS CSI IAM role trust policy and `AmazonEBSCSIDriverPolicyV2` attachment in `infra/terraform/ebs-csi.tf`.
- [x] T005 [US1] Add the `aws-ebs-csi-driver` managed add-on and service-account role reference to `infra/terraform/eks.tf`.
- [x] T006 [US1] Import the existing IAM role, policy attachment, and EKS add-on using the exact resource addresses documented in `specs/024-gitops-stateful-pilot/quickstart.md`. Evidence: role, attachment, and add-on imports succeeded on 2026-08-21.
- [x] T007 [US1] Run `terraform fmt -check -recursive`, `terraform validate -no-color`, and a refreshed plan from `infra/terraform`; stop if destroy or replacement is proposed. Evidence: PASS — plan `0 to add, 2 to change, 0 to destroy`; changes are role/add-on tags and add-on conflict settings.

## Phase 3: Product PostgreSQL desired state (US2)

- [x] T008 [P] [US2] Add the Product PostgreSQL headless Service and StatefulSet with `PGDATA=/var/lib/postgresql/data/pgdata` in `infra/k8s/base/product-postgres/service.yaml` and `infra/k8s/base/product-postgres/statefulset.yaml`.
- [x] T009 [P] [US2] Add the Product PostgreSQL base kustomization in `infra/k8s/base/product-postgres/kustomization.yaml`.
- [x] T010 [US2] Add the six-resource `dev-pilot` overlay and immutable Product image mapping in `infra/k8s/overlays/dev-pilot/kustomization.yaml` and `infra/k8s/overlays/dev-pilot/flash-sale-config.yaml`.
- [x] T011 [US2] Run `kubectl kustomize infra/k8s/overlays/dev-pilot` and confirm exactly six resources with no Secret resource or secret value. Evidence: `specs/024-gitops-stateful-pilot/validation.md`.
- [x] T012 [US2] Run `kubectl apply --dry-run=client -k infra/k8s/overlays/dev-pilot` and record exit status without live mutation. Evidence: `specs/024-gitops-stateful-pilot/validation.md`.

## Phase 4: Safe operator workflow (US3)

- [x] T013 [US3] Add `infra/scripts/gitops/phase9-stateful-pilot.ps1` with validation-by-default, explicit `-Apply`, external Secret name checks, immutable `-Image`, rollout waits, and no PVC deletion or Terraform apply.
- [x] T014 [US3] Run the Phase 9 script without `-Apply` and verify it performs only render/dry-run checks. Evidence: `specs/024-gitops-stateful-pilot/validation.md`.
- [x] T015 [US3] With the existing external Secrets and tested image, run the script with `-Apply`; verify PostgreSQL PVC `Bound`, PostgreSQL `1/1`, Product `1/1`, and internal Services. Evidence: `specs/024-gitops-stateful-pilot/validation.md`.

## Phase 5: Evidence and handoff

- [x] T016 Run `git diff --check` and scan changed Kubernetes manifests for `kind: Secret` or secret values. Evidence: `specs/024-gitops-stateful-pilot/validation.md`.
- [x] T017 Record Terraform, Kustomize, rollout, PVC, EBS CSI, and health evidence in `specs/024-gitops-stateful-pilot/validation.md` after the operator import/plan/apply gate.
- [x] T018 Update this ledger with command, scope, result, and PR reference; leave unrelated user changes unstaged. PR reference is pending commit/push.

## Dependencies and Order

T001–T003 establish the adoption contract. T004–T007 must complete before Terraform owns the EBS
add-on. T008–T012 can proceed in parallel with T004–T007 but must pass before live apply. T013–T015
depend on the overlay and external Secrets. T016–T018 are final handoff gates.

## Parallel Opportunities

- T002 and T003 are independent documentation tasks.
- T008 and T009 can be prepared in parallel with Terraform tasks.
- T016 can run after the first complete diff while T017 collects live evidence.

## Definition of Done

- All tasks have evidence, not only checked boxes.
- Terraform plan has zero destroy/replacement actions.
- Pilot overlay renders six resources and passes client-side dry-run.
- Both pilot workloads are Ready and the PVC remains Bound.
- No secret value, `.env`, or Secret manifest is committed.
- Argo CD and unrelated services remain untouched.
