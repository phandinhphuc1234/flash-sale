# Validation: Cloud Kafka Contract Provisioning

**Status**: Pre-merge implementation verified; live apply and idempotence evidence pending merge

**Date**: 2026-08-22

**Environment**: `flash-sale-dev`, namespace `flash-sale`, Argo Application `flash-sale-cloud`

## Phase 19 prerequisite

- `infra/scripts/gitops/phase19-argocd-cloud.ps1 -Apply -TimeoutSeconds 600`: PASS.
- `flash-sale-cloud`: `Synced|Healthy` at revision
  `7831ce6be91ac67f8f7952cfdc64426acb76bc39`.
- Eight application Deployments became Available and the historical `dev-pilot` Application was
  removed after the ownership handoff.
- `infra/scripts/gitops/phase18-gateway-smoke.ps1 -Run`: PASS; readiness `200`, public Product
  catalog `200`, anonymous admin route `401`.

## Static and build validation

| Command/check | Scope | Result |
|---|---|---|
| PowerShell AST parser | Phase 20 runner plus Campaign, Flash Sale, Order, and Payment registrars | PASS, 5/5 scripts |
| `.\mvnw.cmd -pl contracts/kafka-avro-contracts -am verify` | Seven accepted AVSC sources and generated SpecificRecords | PASS, 13 tests, 0 failures/errors/skips |
| `kubectl kustomize infra/k8s/overlays/cloud` | Complete cloud desired state | PASS; explicit `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`; seven Payment/Stripe runtime flags remain `false` |
| `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud` | Complete cloud desired state | PASS, all resources renderable |
| Registrar mock/static checks | Exact schema lookup, latest identity, compatibility, and `-CheckOnly` mutation guard | PASS for 9/9 subjects |
| Runner schema-transform invocation | All accepted AVSC files, including nullable defaults and string unions | PASS, 7/7 schemas |
| Native-process deadline guard | Child command intentionally slept beyond a one-second test deadline | PASS; process tree stopped and bounded error returned in 1.256 seconds |
| `git diff --check` | Phase 19 evidence plus Phase 20 implementation | PASS |

The first Maven wrapper attempt inside the restricted sandbox could not download Maven and failed
with `SocketException: Permission denied: connect`. The authorized rerun completed successfully;
this was an execution-environment network restriction, not a contract failure.

## Validation-only live inventory

Command:

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase20-kafka-contracts.ps1
```

Expected pre-merge result: exit code `2`, meaning the approved state is pending without mutation.

Observed:

- Kubernetes context: `flash-sale-dev`.
- Live Payment flags: 7/7 disabled.
- Kafka automatic topic creation policy: not yet configured on the live StatefulSet.
- `campaign.lifecycle.v1`: one partition, RF1, total end offset `0`; safe candidate for expansion.
- `flashsale.purchase.events.v1`: one partition, RF1, total end offset `0`; safe candidate for
  expansion.
- Five remaining approved topics: absent.
- Schema Registry accepted subjects: `0`; nine approved subjects are pending.
- Temporary localhost port `28081` had zero listeners after exit, proving child cleanup.
- Output ended with `Validation-only mode: no broker, registry, Argo, workload, or Secret state was changed.`

## Pre-merge apply rejection

Command:

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase20-kafka-contracts.ps1 -Apply
```

Observed expected result: exit code `1` with
`Run Phase 20 -Apply only from a clean develop branch exactly matching freshly fetched origin/develop.`
The runner fetched `origin/develop`, compared the exact branch revision and Phase 20 asset state,
and stopped before Argo annotation, topic changes, or schema registration.

Post-rejection live evidence:

- Argo: `Synced|Healthy` at the unchanged Phase 19 revision
  `7831ce6be91ac67f8f7952cfdc64426acb76bc39`.
- Kafka topics remained exactly `_schemas`, `__consumer_offsets`, `campaign.lifecycle.v1`, and
  `flashsale.purchase.events.v1`.
- Both application topics remained at one partition/RF1 with end offset `0`.
- Schema Registry subject count remained `0`.

## Remaining live gate

T014–T015 remain open. After this branch is reviewed and merged, switch to a clean, freshly pulled
`develop` and run:

```powershell
.\infra\scripts\gitops\phase20-kafka-contracts.ps1 -Apply
.\infra\scripts\gitops\phase20-kafka-contracts.ps1
.\infra\scripts\gitops\phase20-kafka-contracts.ps1 -Apply
```

The first apply must materialize seven topics and nine subjects. The validation-only run must then
exit `0`, and the second apply must preserve topic topology, schema IDs, schema versions, and
compatibility values unchanged.
