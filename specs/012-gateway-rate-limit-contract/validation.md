# Validation Evidence: Gateway Rate-Limit Error Contract

**Feature**: `012-gateway-rate-limit-contract`
**Environment**: Windows, Java 21 repository workspace
**Status**: Verified

## Artifact Gate

| Check | Command or inspection | Result |
|-------|-----------------------|--------|
| Active feature | Inspect `.specify/feature.json` | Points to `specs\\012-gateway-rate-limit-contract` |
| Spec Kit prerequisites | `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` | Exit 0; spec, plan, research, contract, quickstart, and tasks resolved |
| Approval and decisions | Inspect `spec.md`, `plan.md`, and `tasks.md`; search unresolved markers | Approved by the user's explicit request; no blocking clarification or placeholder |
| Requirements checklist | Inspect `checklists/requirements.md` | 14/14 complete; 0 incomplete |
| Contract-first gate | Inspect `contracts/gateway-rate-limit-error-http.md` before production change | Exact additive 429 row exists; quota/TTL/key/backend/header policies explicitly deferred |
| Cross-artifact analysis | Map 8 FR, 3 NFR, and 3 SC across spec/plan/tasks/contract | 100% covered; 0 CRITICAL/HIGH/MEDIUM inconsistency found in the local analysis |

**Gate result**: PASS. T001 and T002 are complete; test-first production work may begin.

## Test-First Evidence

| Phase | Command | Result |
|-------|---------|--------|
| Expected red | `.\mvnw.cmd -pl services/api-gateway -am "-Dtest=GatewayErrorCodeTests,GatewayHttpErrorWriterTests" test` before production enum | Build/test failure as expected: `RATE_LIMIT_EXCEEDED cannot be resolved`; 7 test executions, 3 errors attributable only to the missing enum member |
| Focused green | Same focused taxonomy/writer command after T005 | Exit 0; 14 tests, 0 failures, 0 errors, 0 skipped |
| Downstream ownership | `.\mvnw.cmd -pl services/api-gateway -am "-Dtest=GatewayProxyPassThroughTests" test` | Exit 0; 5 cases including downstream 429, 0 failures, 0 errors, 0 skipped |

The direct writer test also proves that `Retry-After`, `RateLimit`, and `X-RateLimit-*` headers are
absent while their semantics remain unapproved. No limiter/filter/backend was invoked.

## Required Module Gate

| Command | Reactor scope | Exit status | Result |
|---------|---------------|-------------|--------|
| `.\mvnw.cmd -pl services/api-gateway -am verify` | `flash-sale-engine`, `api-gateway` | 0 | BUILD SUCCESS; 105 tests, 0 failures, 0 errors, 0 skipped; executable Gateway JAR packaged |

**Completed**: 2026-07-22 17:17:37 +07:00

The first sandboxed Maven Wrapper attempt could not open the network connection required by the
wrapper. The same repository command was rerun with the permitted Maven execution context and
completed successfully; this was an execution-environment restriction, not a product failure.

## Final Reconciliation

- 8/8 functional requirements, 3/3 non-functional requirements, and 3/3 success criteria are
  represented by the approved contract, implementation, tests, or technology documentation.
- 14/14 requirements-checklist entries and 9/9 implementation tasks are complete.
- `RATE_LIMIT_EXCEEDED` is the only production-code addition in Feature 012; no classifier maps an
  arbitrary failure to it and no rate-limit filter/backend is active.
- No quota, TTL, key strategy, Redis failure policy, retry policy, `Retry-After`, `RateLimit`, or
  `X-RateLimit-*` behavior was introduced.
- A downstream HTTP 429 remains byte-for-byte downstream-owned in the proxy regression test.
- No tracing dependency or runtime configuration was added. The technology guide records the future
  standard as Micrometer Tracing -> OpenTelemetry bridge/exporter -> OTLP -> root-owned OpenTelemetry
  Collector -> Tempo, with W3C propagation and no direct OpenTelemetry SDK coupling in policy code.
- Existing `X-Trace-Id`/UUID behavior remains request correlation and is not claimed to be complete
  distributed tracing.

**Final result**: PASS. Feature 012 is verified and ready for a separately specified runtime
rate-limiting feature.
