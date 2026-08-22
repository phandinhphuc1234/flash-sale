# Phase 20 Quickstart

## Prerequisites

- Phase 19 is complete: `flash-sale-cloud` is `Synced` and `Healthy`.
- `kubectl` targets `flash-sale-dev`.
- Kafka and Schema Registry are ready in namespace `flash-sale`.
- Phase 20 is reviewed and merged into `develop` before live apply.

## Pre-merge validation

From the repository root:

    .\mvnw.cmd -pl contracts/kafka-avro-contracts -am verify
    kubectl kustomize infra/k8s/overlays/cloud
    kubectl apply --dry-run=client -k infra/k8s/overlays/cloud
    .\infra\scripts\gitops\phase20-kafka-contracts.ps1

Before first apply, validation is expected to report the five missing topics, two expandable empty
topics, and nine missing subjects without changing live state.

## Live apply after merge

    git fetch origin develop
    git switch develop
    git pull --ff-only origin develop

    kubectl -n argocd annotate application flash-sale-cloud `
      argocd.argoproj.io/refresh=hard --overwrite
    kubectl -n argocd get application flash-sale-cloud --watch

    .\infra\scripts\gitops\phase20-kafka-contracts.ps1 -Apply -TimeoutSeconds 600
    .\infra\scripts\gitops\phase20-kafka-contracts.ps1
    .\infra\scripts\gitops\phase20-kafka-contracts.ps1 -Apply -TimeoutSeconds 600

## Expected result

- Seven topics have three partitions and RF1.
- Nine exact subjects are latest and use `BACKWARD_TRANSITIVE`.
- The second apply creates no schema version and changes no topic topology.
- Kafka auto topic creation is disabled.
- `flash-sale-cloud` remains `Synced` and `Healthy`.
- Payment feature flags remain disabled.
