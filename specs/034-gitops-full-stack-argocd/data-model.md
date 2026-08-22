# Data Model: Full-Stack Argo CD Ownership

This feature introduces no business data or persistent schema. Its operational state model is:

| State | Meaning | Allowed next state |
|---|---|---|
| Validated | Manifests, cluster, workloads, and safety guards pass | PilotSuspended |
| PilotSuspended | Historical Application remains present but auto-sync is disabled | CloudApplied, PilotRestored |
| CloudApplied | Full-cloud Application exists and reconciliation has started | CloudHealthy, PilotRestored |
| CloudHealthy | Cloud Application is Synced/Healthy and eight Deployments are available | PilotRetired |
| PilotRetired | Historical Application object is removed without cascading workload deletion | Complete |
| PilotRestored | Failed cutover removed the cloud Application object and restored pilot auto-sync | Failed |

Invariant: at most one Application may actively self-heal overlapping Product resources.

Invariant: no transition deletes workloads, Secrets, PVCs, or migration Jobs.
