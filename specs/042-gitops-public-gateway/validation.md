# Phase 23 Validation Evidence

**Date**: 2026-08-23
**Environment**: AWS EKS `flash-sale-dev`, namespace `flash-sale`
**Argo Application**: `flash-sale-cloud`

## Live public Gateway smoke

| Check | Result |
|---|---|
| Argo sync/health | PASS — `Synced`, `Healthy` |
| Argo revision | `60c5caf7cc3b7e82134e5e13333ca89425d4064e` |
| Public Service inventory | PASS — only `api-gateway` is `LoadBalancer`; backend/platform Services remain private |
| AWS endpoint | `a057f0e08f8294ea88ba8d98ef9dcc53-35d08a72d8cdea1f.elb.ap-southeast-2.amazonaws.com:8080` |
| Gateway readiness | PASS — HTTP `200` |
| Public catalog route | PASS — HTTP `200` |
| Anonymous admin boundary | PASS — HTTP `401` |
| Mutation boundary | PASS — smoke helper performed no Kubernetes mutation |

Command:

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase23-public-gateway.ps1 -Run -TimeoutSeconds 180
```

The AWS-generated hostname is development-only and HTTP-only. No password, token, cookie,
Kubernetes Secret value, `.env` value, or Authorization header was recorded.

## Note

The first probe attempted port `80` and timed out because the Kubernetes Service exposes port
`8080`. The runner was corrected to call `http://<aws-hostname>:8080`; the rerun passed.

## Remaining gate

GitOps rollback still needs to be executed and evidenced. Rollback must restore `api-gateway` to
`ClusterIP` and must not delete PVCs, Secrets, topics, or application data.
