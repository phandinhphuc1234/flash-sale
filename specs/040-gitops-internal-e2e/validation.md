# Phase 22 Validation Ledger

Evidence is sanitized by design. Passwords, JWTs, cookies, Kubernetes Secret values, and full
request bodies must never be recorded here.

| Check | Command / scope | Result | Evidence |
|---|---|---|---|
| Inventory fixture unit tests | `./mvnw.cmd -pl services/inventory-service -am -Dtest=InventoryFixtureCommandLineRunnerTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS | 3 tests, 0 failures, 0 errors |
| Phase 22 PowerShell parser | AST parse of `infra/scripts/gitops/phase22-internal-e2e.ps1` | PASS | PowerShell 7 parser returned no errors |
| Phase 22 static safety checks | `infra/scripts/gitops/tests/phase22-internal-e2e.tests.ps1` | PASS | PowerShell parser, required flow markers, and forbidden data-plane command checks passed |
| Cloud Kustomize render/dry-run | `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud` | PASS | Cloud overlay rendered all application/platform resources; no live resource changed |
| Full Maven reactor (default) | `./mvnw.cmd clean verify` | ENVIRONMENTAL FLAKE | One order concurrency test exceeded the local 2s Hikari connection wait while nine concurrent workers contended for the test pool; no Phase 22 assertion failed |
| Full Maven reactor (test-only timeout override) | `./mvnw.cmd clean verify "-Dspring.datasource.hikari.connection-timeout=30000"` | PASS | 13/13 reactor modules; BUILD SUCCESS; 0 failures/errors; intentional skips only; 29:51. The property was supplied only to the test JVM and did not change production configuration |
| Spec Kit consistency analysis | `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, marker scan, and cross-artifact review | PASS | Feature directory resolved; required artifacts present; no unresolved clarification/TODO/TBD/CRITICAL/HIGH markers |
| Authenticated internal E2E | `phase22-internal-e2e.ps1 -Run -AdminLogin <existing-login>` | PENDING | Requires promoted Inventory image and operator-entered admin password |

## Live evidence template

Record only non-secret identifiers and statuses after a successful run:

- Date/time and Argo `flash-sale-cloud` revision:
- EKS context name:
- Admin authority check: `CATALOG_ADMIN/INVENTORY_ADMIN/CAMPAIGN_ADMIN` present (yes/no):
- Product/Variant/Campaign IDs:
- Inventory fixture Job status:
- Reservation `202`, replay identity match, owner lookup status:
- Order ID, status, and four identity-match result:
- Cleanup outcome and elapsed seconds:
