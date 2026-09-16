# Infrastructure Scripts

Repository-owned infrastructure automation lives here.

- [`gitops/`](gitops/README.md) contains guarded validation, provisioning, migration, smoke,
  rollback, observability, and capacity helpers.
- `docs/verify-api-documentation.ps1` checks the HTTP inventory and local OpenAPI wiring.
- `tests/` contains Pester/static tests for cross-cutting script behavior.

Scripts default to validation/read-only mode where practical. A flag such as `-Apply`, `-Run`,
`-Push`, or `-ForceRerun` is an explicit mutation boundary; read the script help and GitOps guide
before using it. Never paste secrets into command history when the script supports a secure prompt.
