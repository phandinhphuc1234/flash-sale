# Data Model: Environment Contract and Secret Inventory

This phase introduces repository metadata, not business persistence. The structures below describe
the configuration vocabulary used by the next GitOps phases.

## Environment Contract

| Field | Meaning | Allowed values |
|---|---|---|
| `name` | Operator-facing environment label | `local`, `cloud` |
| `sourceOfTruth` | Repository path that owns the environment | `infra/docker`, `infra/k8s/overlays/cloud` |
| `runtime` | Where workloads execute | Docker Compose, AWS EKS |
| `reconciler` | Component that applies changes | Manual Compose, Argo CD |
| `publicIngress` | Whether this phase exposes a public endpoint | `false` for both |

## Secret Inventory Entry

| Field | Meaning | Example name (key only) |
|---|---|---|
| `key` | Name consumed by a service or bootstrap script | `POSTGRES_PASSWORD` |
| `owner` | Service/platform responsible for the secret | `platform`, `authentication-service`, `payment-service` |
| `source` | Operator-managed location | ignored `.env` or Kubernetes Secret |
| `requiredPhase` | First phase that needs the value | 14 or 15 |
| `value` | Secret material | Never stored in this repository |

## Invariants

- No third environment label may be introduced as a deployment target.
- A secret inventory entry may document a key but never its value.
- Application service ownership remains unchanged; this metadata cannot authorize cross-service
  database access.
