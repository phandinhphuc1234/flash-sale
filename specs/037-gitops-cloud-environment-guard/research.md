# Phase 22 Research

## Decision 1: Read-only guard rather than a new reconcile action

**Decision**: Reuse `kubectl` metadata and client-side Kustomize validation; do not add `-Apply`.

**Rationale**: Argo CD already owns desired state. A second mutation path would create ownership
drift and is unnecessary for a configuration safety gate.

**Alternatives considered**: A script that reapplies the overlay was rejected because it could
override Argo timing and violate the phase's no-mutation boundary.

## Decision 2: Secret existence without Secret data

**Decision**: Query Secret resources using `kubectl get <name> -o name` only.

**Rationale**: Phase 15 already provisions and validates the input mappings. Phase 22 needs only
the boundary names; requesting `.data` would unnecessarily handle secret material.

**Alternatives considered**: Listing data keys via JSON was rejected because the response contains
base64-encoded values even if the script does not print them.

## Decision 3: Argo policy is part of the environment contract

**Decision**: Require repository, path, target revision, destination, `selfHeal=true`, and `prune=false`.

**Rationale**: These fields determine who owns cloud state and whether drift is repaired or deleted.

**Alternatives considered**: Checking only `Synced/Healthy` was rejected because a healthy Application
can still point at the wrong repository or enable unsafe pruning.

## Decision 4: No public cloud entry point in this phase

**Decision**: Reject `LoadBalancer` and `NodePort` Services and do not introduce Ingress, DNS, or TLS.

**Rationale**: The project has local and cloud-only environments, and the current roadmap explicitly
keeps the gateway internal while deployment verification is being learned.

**Alternatives considered**: Adding an ingress now was rejected because it expands security and DNS
scope beyond a configuration guard.
