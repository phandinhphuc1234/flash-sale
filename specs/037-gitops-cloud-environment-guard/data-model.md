# Phase 22 Guard Data Model

This operational feature has no business persistence. The verifier uses transient metadata records.

| Record | Fields | Meaning |
|---|---|---|
| `ArgoOwnership` | application, repo, path, targetRevision, destination, sync, health, selfHeal, prune | Desired-state owner and policy observed from Argo CD |
| `WorkloadBoundary` | workload, configMaps, secret, jwtMount | Runtime configuration references for an application workload |
| `PlatformSafety` | serviceTypes, kafkaAutoCreate, kafkaLogDirs, passwordRefCount | Cloud platform exposure and Secret-reference invariants |
| `GuardResult` | checkName, status, diagnostic, readOnly | One pass/fail observation emitted by the operator command |

No record contains Secret values, `.env` values, JWT contents, or persisted state.
