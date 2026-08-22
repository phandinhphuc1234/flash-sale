# Data Model: Cloud Release Artifact Verification

This feature has no durable domain data. The following are ephemeral observations emitted by the
operator workflow and recorded in validation evidence.

## ReleaseArtifactObservation

| Field | Meaning |
|---|---|
| service | One of the eight approved application services |
| desiredImage | Image reference selected by the live Deployment |
| repository | ECR repository derived from the service |
| tag | Image tag parsed from the desired reference |
| ecrDigest | Manifest digest returned by ECR |
| podDigest | Digest from the selected Pod image ID |
| available | Whether the Deployment has the requested available replicas |
| result | Match or failure classification |

## CloudReleaseVerificationRun

| Field | Meaning |
|---|---|
| cluster | Expected EKS cluster context |
| argoApplication | `flash-sale-cloud` sync/health/revision observation |
| releaseArtifacts | Eight `ReleaseArtifactObservation` values |
| paymentFlags | Seven disabled runtime flag observations |
| gatewaySmoke | Readiness, catalog, and admin outcomes |
| outcome | Pass/fail and bounded diagnostics |

These records are not stored in PostgreSQL, Redis, Kafka, or Kubernetes. They are operator evidence
only.
