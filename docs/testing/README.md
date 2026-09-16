# Testing Guide

Tests are owned as close as possible to the behavior they prove:

- service unit/integration/architecture tests under `services/<service>/src/test`;
- Avro compatibility tests under `contracts/kafka-avro-contracts/src/test`;
- cross-service local smoke tests under `infra/docker/smoke`;
- infrastructure script tests under `infra/scripts/**/tests`;
- controlled performance scenarios under [`load-tests/`](../../load-tests/README.md);
- dated acceptance evidence under the governing `specs/<feature>/validation.md`.

## Standard gates

```powershell
.\mvnw.cmd -pl services/payment-service -am verify
.\mvnw.cmd clean verify
pwsh -NoLogo -NoProfile -File .\infra\scripts\docs\verify-api-documentation.ps1
kubectl apply --dry-run=client -k infra/k8s/overlays/cloud
```

Do not report a full build as passing when only one module ran. A local pass does not prove cloud
networking, IAM, DNS, provider webhook, or Argo behavior.
