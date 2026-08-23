# Phase 23 Tasks: Development Public Gateway

## Planning gate

- [x] T001 Confirm the exposure mechanism and HTTP-only development boundary: EKS Service LoadBalancer/NLB with AWS-generated HTTP hostname.
- [x] T002 Accept `docs/adr/0026-public-gateway-development-exposure.md` for the development-only boundary.

## Implementation

- [ ] T003 Add the approved public edge patch to the cloud overlay only; keep all other Services internal.
- [ ] T004 Add bounded endpoint discovery and public Gateway smoke under `infra/scripts/gitops/`.
- [ ] T005 Add a deterministic GitOps rollback path and operator runbook.

## Verification and evidence

- [ ] T006 Run Kustomize client-side dry-run for cloud and local overlays as applicable.
- [ ] T007 Verify Argo `Synced/Healthy`, exactly one public Service, endpoint readiness/catalog/admin boundary.
- [ ] T008 Verify rollback restores `ClusterIP` and does not delete PVCs, Secrets, topics, or data.
- [ ] T009 Record sanitized evidence in `specs/042-gitops-public-gateway/validation.md` and update the roadmap.
