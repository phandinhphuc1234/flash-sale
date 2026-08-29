# Validation Evidence: Authenticated Cart MVP

This ledger records G1 validation only. Secret values, access tokens, Authorization headers, and
private response bodies must never be copied here.

| Task | Command / scope | Result | CI/PR reference |
|------|-----------------|--------|-----------------|
| T001 | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am -DskipTests compile` | PASS (BUILD SUCCESS, exit 0) | Local |
| T002 | Same Cart compile gate; resource copy included `application.yml` and Liquibase changelog | PASS (exit 0) | Local |
| T003 | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am '-Dtest=CartArchitectureTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` | PASS (2 tests, 0 failures, exit 0) | Local |
| T004 | Evidence ledger created | PASS | |
| G1 gate | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am test` | PASS (Cart + common-web: 12 tests, 0 failures, 0 errors, exit 0) | Local |

## Evidence rules

- Record the command, scope, exit status, and safe summary only.
- Keep `CART_CLIENT_SECRET` in ignored `infra/docker/.env`; automation may check presence without
  printing or persisting its value.
- G1 does not create database tables, call Product, or change Gateway/cloud runtime behavior.
