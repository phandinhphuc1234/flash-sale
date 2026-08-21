# Implementation Plan: Argo CD Dev Pilot Bootstrap

**Branch**: `codex/gitops-phase10-argocd` | **Date**: 2026-08-21 | **Spec**: [spec.md](spec.md)
**Status**: Approved for implementation

## Summary

Bootstrap a pinned non-HA Argo CD control plane with the official v3.4.2 manifest, keep it internal,
and create a source-controlled Application for the Phase 9 `dev-pilot` overlay. A PowerShell script
defaults to validation and requires `-Apply` for cluster mutation.

## Technical Context

| Area | Decision |
|---|---|
| Cluster | Existing EKS `flash-sale-dev` / `ap-southeast-2` |
| Argo install | Official non-HA manifest pinned to `v3.4.2` |
| Namespace | `argocd` |
| Server exposure | Existing `ClusterIP`; operator port-forward |
| Application source | Public GitHub repository, `develop`, `infra/k8s/overlays/dev-pilot` |
| Sync | Automated + self-heal, `prune: false` |
| Secret source | Existing external Secrets; Argo CD never receives values from Git |
| Ownership | Argo CD owns pilot K8s resources; Terraform owns AWS/EKS |

## Constitution Check

| Rule | Result | Evidence |
|---|---|---|
| Root infrastructure ownership | PASS | Script/Application/ADR live under root `infra/` and `docs/adr`. |
| Controlled ingress | PASS | Argo server and pilot services remain internal. |
| Single desired-state owner | PASS | Application owns pilot; Terraform owns AWS; Secrets stay operator-managed. |
| Secret safety | PASS | No secret value or admin password is printed or committed. |
| Kubernetes validation | REQUIRED | Bootstrap validation and post-install status tasks. |
| Architecture decision | PASS | ADR 0008 records source-of-truth and rollback. |

## Design

### Control plane bootstrap

The script uses the pinned URL
`https://raw.githubusercontent.com/argoproj/argo-cd/v3.4.2/manifests/install.yaml`.
It creates/updates only the `argocd` namespace and official install resources. It checks that
`argocd-server` remains `ClusterIP` after apply.

### Application resource

The source-controlled Application manifest is `infra/k8s/argocd/application-dev-pilot.yaml`. It is
applied after the CRDs exist. Its source coordinates and destination are fixed by the contract; its
sync policy self-heals but does not prune.

### Operator safety

Without `-Apply`, the script checks the pinned URL, renders the Application manifest, and performs
client-side validation where the CRD is available. With `-Apply`, it waits for Argo deployments,
applies the Application, waits for its status, and prints only resource names/statuses. It does not
read the admin Secret data.

## Project Structure

```text
infra/k8s/argocd/
└── application-dev-pilot.yaml
infra/scripts/gitops/
└── phase10-argocd-bootstrap.ps1
docs/adr/0008-argocd-dev-pilot-source-of-truth.md
specs/025-argocd-dev-pilot/
```

The upstream Argo CD installation manifest remains version-pinned in the script rather than being
duplicated in the repository. Kustomize remains the source of truth for the Product pilot workloads.

## Validation Strategy

| Layer | Command/check | Expected |
|---|---|---|
| Script parser | PowerShell parser | PASS |
| Application render | `kubectl kustomize infra/k8s/argocd` | one Application |
| Default script | `phase10-argocd-bootstrap.ps1` | no live mutation |
| Argo install | `kubectl -n argocd rollout status ...` | all core workloads Ready |
| Service exposure | `kubectl -n argocd get svc argocd-server` | `ClusterIP` |
| Application | `kubectl -n argocd get application dev-pilot` | `Synced`, `Healthy` |
| Pilot regression | `kubectl -n flash-sale get pods,pvc` | Product/PostgreSQL Ready; PVC Bound |

Maven tests are not applicable because no Java source changes occur.

## Complexity Tracking

Non-HA Argo CD and no-prune sync are deliberate development safeguards. ADR 0008 records the
production-hardening work deferred to a later feature.
