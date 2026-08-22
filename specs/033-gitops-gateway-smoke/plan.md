# Implementation Plan: Cloud Gateway Smoke

Branch: codex/gitops-phase18-gateway-smoke
Spec: specs/033-gitops-gateway-smoke/spec.md

## Summary

Add a value-safe PowerShell operator script that validates the existing cloud overlay and, when
explicitly requested, port-forwards the internal API Gateway for a bounded smoke test. No
application or Kubernetes Service behavior changes.

## Design

1. Resolve the repository root and use the active kubectl context.
2. Run Kustomize render and client-side dry-run, then assert namespace, Service, and Deployment
   readiness.
3. In -Run mode, reject an occupied local port, then start kubectl port-forward bound explicitly
   to 127.0.0.1 as a hidden child process with temporary stdout/stderr files.
4. Poll /actuator/health/readiness, call /api/v1/catalog/products?page=0&size=1, and call
   /api/v1/admin/catalog/products?page=0&size=1 without credentials.
5. Accept only readiness/catalog 200 and admin 401 or 403; stop the child process in cleanup.

## Safety and validation

- This is root-owned infrastructure under infra/scripts/gitops/.
- No Secret values, JWT material, or token is read or printed.
- The Service remains ClusterIP; port-forward is temporary and local-only.
- Validate PowerShell syntax, Kustomize dry-run, validation-only mode, and the operator smoke.
