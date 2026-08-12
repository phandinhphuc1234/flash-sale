# Rules for extending Authentication

Use this checklist when adding a new authentication capability.

1. Put account/session invariants in `domain/account` or `domain/session` as Java-only code.
2. Put orchestration, commands, results, and capability ports in the matching `application/<capability>` package.
3. Put REST controllers, DTOs, filters, and schedulers under `adapter/in`.
4. Put JPA, Redis, JWT, and provider-specific code under `adapter/out`.
5. Keep Spring/JPA/Redis/Nimbus types out of `domain` and `application`.
6. Add a mapper at every boundary; never reuse a JPA entity as an HTTP response.
7. Add class-level documentation when a type introduces a new boundary or security decision.
8. Update the approved spec/plan/tasks before adding observable behavior, dependencies, or schema.
9. Add tests at the layer where the risk lives: pure unit tests for invariants, integration tests
   for PostgreSQL/Redis/locking, and contract tests for HTTP behavior.
10. Record validation evidence before checking a task complete.
