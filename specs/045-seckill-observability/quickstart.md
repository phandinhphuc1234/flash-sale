# Quickstart: Phase 25 Seckill Observability

## 1. Static validation

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase25-seckill-observability.ps1
```

This is validation-only. It must not change Kubernetes state.

## 2. Merge before live apply

```powershell
git switch develop
git pull --ff-only origin develop
```

Argo targets `develop`, so applying from an unmerged feature branch is intentionally rejected.

## 3. Deploy

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase25-seckill-observability.ps1 -Apply
```

The first apply securely asks for the Grafana administrator password. Repeated applies reuse the
existing Secret.

## 4. Inspect and open Grafana

```powershell
kubectl -n argocd get application flash-sale-observability
kubectl -n monitoring get pods,svc
kubectl -n monitoring port-forward service/grafana 3000:3000
```

Open `http://127.0.0.1:3000` and choose **Flash Sale - Seckill Overview**.

## 5. Generate meaningful seckill data

Run the existing internal E2E or Stripe smoke, then refresh the dashboard:

```text
Flash Sale reservation -> Order Purchase Saga -> Payment -> reservation confirmation
          |                       |                  |
      Redis repair            outbox/DLT        recovery/outbox
```

## Rollback

Revert the Git change or suspend/delete only `flash-sale-observability`. Do not delete
`flash-sale-cloud`. Monitoring history is disposable; application business data is unaffected.
