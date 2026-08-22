# Implementation Plan: Cloud Environment and Configuration Guard

**Branch**: `codex/gitops-phase22-cloud-guard` | **Date**: 2026-08-22 | **Spec**: [spec.md](spec.md)

## Summary

Add a read-only PowerShell 7 operator guard for the single cloud EKS environment. The guard uses
bounded `kubectl` processes, validates the cloud Kustomize overlay and Argo Application metadata,
checks workload references and platform safety settings, and verifies Secret names without requesting
Secret data. It complements Phase 21 release verification and does not mutate the cluster.

## Technical Context

**Language/Version**: PowerShell 7+, Kubernetes CLI, repository scripts

**Primary Dependencies**: Existing `kubectl`, Argo CD Application CR, Kustomize via kubectl; no new
production dependency

**Storage**: Kubernetes metadata only; no database or Secret value access

**Testing**: PowerShell AST parse, `kubectl kustomize`, `kubectl apply --dry-run=client`, live EKS
read-only guard, and `git diff --check`

**Target Platform**: Windows operator workstation controlling AWS EKS `flash-sale-dev`

**Project Type**: Repository-level GitOps operator script and evidence

**Performance Goals**: Complete within the existing 600-second bounded execution budget

**Constraints**: Read-only; no `.env` writes; no Secret values; no public ingress; Payment remains
disabled

**Scale/Scope**: One EKS cluster, one Argo Application, eight application Deployments, three
platform StatefulSets, one Schema Registry Deployment, and approved Secret/ConfigMap names

## Constitution Check

- Specification traceability: PASS; FR-001–FR-007 map to the guard and evidence.
- Service ownership: PASS; no service code, schema, or database is changed.
- Communication: PASS; no routes or contracts change; Kubernetes Service/DNS is only inspected.
- Data and messaging: PASS; PostgreSQL/Redis/Kafka state is not changed; Kafka safety settings are
  verified only.
- Root infrastructure ownership: PASS; script is under `infra/scripts/gitops`.
- Observability: PASS; existing health/Gateway smoke remains the runtime evidence; no registry code
  changes.
- Contracts and dependencies: PASS; no contracts or production dependencies are added.
- Validation: PASS; static parsing, Kubernetes dry-run, live read-only checks, and diff check cover
  this operational change. Maven/load layers do not apply.

## Design

1. Resolve repository root from the script path and require PowerShell 7.
2. Execute `kubectl` through a bounded process helper that captures only bounded diagnostics.
3. Validate current context, cloud Kustomize rendering, Argo source/sync policy, ConfigMap/Secret
   references, auth JWT mount, platform Secret references, private Services, Kafka settings, and
   Payment flags.
4. Query Secret resources only with `kubectl get ... -o name`; never request `.data` or print values.
5. Exit non-zero on the first drift and print a final PASS only when every check succeeds.

## Project Structure

```text
infra/scripts/gitops/
└── phase22-cloud-guard.ps1
specs/037-gitops-cloud-environment-guard/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── tasks.md
├── validation.md
└── checklists/requirements.md
```

**Structure Decision**: Keep the guard with existing GitOps scripts and keep feature evidence in a
sequential Spec Kit directory. No Kubernetes manifest or service source is changed.

## Complexity Tracking

No constitution violations.
