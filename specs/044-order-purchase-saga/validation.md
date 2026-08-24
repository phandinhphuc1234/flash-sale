# Validation ledger: Order-Owned Purchase Saga Completion

Evidence is appended by coherent task group. Secret values, authorization headers, provider
payloads, webhook signatures, JWTs, and Checkout URLs must never be recorded here.

## G1 — Schema-first contract surface — 2026-08-24

| Check | Result | Evidence / boundary |
|---|---|---|
| Approved artifacts | PASS | `spec.md` and `plan.md` record owner approval; `tasks.md` defines the dependency-ordered implementation groups. |
| Avro generated records and contract tests | PASS | `.\mvnw.cmd --batch-mode --no-transfer-progress -pl contracts/kafka-avro-contracts -am verify`: 21 tests, 0 failures, 0 errors, 0 skipped; reactor `BUILD SUCCESS`. |
| Local topic provisioning | PASS | `init-purchase-saga-topics.sh` verified `flashsale.purchase.commands.v1` plus three approved consumer DLTs at 3 partitions and replication factor 1. Repeated execution remained expand-only and non-destructive. |
| Schema Registry provisioning | PASS | Eight main topic/record subjects plus six DLT topic/record bindings registered and re-verified at `BACKWARD_TRANSITIVE`; repeated execution retained version 1 and exact latest Git schema identity. |
| Bounded G1 runner | PASS | `pwsh -NoLogo -NoProfile -File .\infra\docker\smoke\feature-044-purchase-saga.ps1 -Scenario Contracts` exited 0 and emitted `FEATURE_044_CONTRACTS=PASS`. |
| Kafka Compose readiness | PASS | Broker probe measured 13.43 seconds on Docker Desktop; Kafka-only health timeout was raised from 5 to 20 seconds. Broker and Schema Registry became healthy without deleting volumes or topic data. |
| Contract governance docs | PASS | Topic catalog and Avro governance document the approved records, key rules, 23-event/7-command totals, DLT ownership, 14 Registry subject bindings, and late-success correction path. |

G1 performs no application image build, ECR push, EKS rollout, or cloud mutation. Those gates remain
deferred until the full local implementation is green as required by the approved plan.
