# Validation Evidence: One-Time Authentication Admin Bootstrap

**Feature**: `041-authentication-admin-bootstrap`
**Branch**: `codex/auth-admin-bootstrap`
**Date**: 2026-08-22

## Automated evidence

| Check | Command | Result |
|---|---|---|
| Application/domain tests | `./mvnw.cmd -pl services/authentication-service -am -Dtest=BootstrapAdminAccountServiceTests,AccountTests -Dsurefire.failIfNoSpecifiedTests=false test` | PASS — 7 tests, 0 failures, 0 errors |
| Authentication module verify | `./mvnw.cmd -pl services/authentication-service -am verify` | PASS — 64 Authentication tests, 0 failures, 0 errors |
| Launcher static safety | `pwsh -NoLogo -NoProfile -File infra/scripts/gitops/tests/phase22-admin-bootstrap.tests.ps1` | PASS |
| Live launcher validation-only | `phase22-admin-bootstrap.ps1 -AdminEmail admin@example.test -AdminUsername admin` | PASS — generated Job dry-run; no Secret, Job, or auth_db mutation |
| Cloud overlay client validation | `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud` | PASS — rendered cloud resources; no live resource changed |

## Pending release evidence

- Authentication image must contain Feature 041 and be promoted through the existing reviewed
  eight-service image workflow.
- After promotion, run the launcher with `-Apply` and record only sanitized Job status/subject.
- Run Phase 22 authenticated smoke and record its sanitized result and Argo revision.
- No password, hash, JWT, cookie, or Secret data may be added to this document.
