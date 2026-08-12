# Quickstart: Verify Gateway Rate-Limit Error Contract

Run from the repository root on Java 21.

## Focused contract tests

```powershell
.\mvnw.cmd -pl services/api-gateway -am "-Dtest=GatewayErrorCodeTests,GatewayHttpErrorWriterTests" test
```

Expected:

- `RATE_LIMIT_EXCEEDED` is the eighth exact taxonomy row;
- the writer returns HTTP 429 and `application/json`;
- the body is exactly `{code,message,traceId}` with `RATE_LIMIT_EXCEEDED`, `Too many requests`, and a
  non-blank correlation value;
- all seven prior rows remain unchanged.

## Downstream ownership regression

```powershell
.\mvnw.cmd -pl services/api-gateway -am "-Dtest=GatewayProxyPassThroughTests" test
```

Expected: representative downstream error bodies, including the added downstream 429 scenario, pass
through without conversion to the Gateway envelope.

## Required module gate

```powershell
.\mvnw.cmd -pl services/api-gateway -am verify
```

Expected: exit code 0 and all Gateway tests pass.

No Redis, rate-limit filter, OTLP endpoint, Collector, Docker, or Kubernetes runtime is needed for
this contract-only verification.
