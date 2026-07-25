# Quickstart: Verify Gateway-Owned Error Handling

**Feature**: `011-gateway-error-handling`

This guide verifies the feature locally without running authentication-service, product-service,
PostgreSQL, Kafka, or Redis. The tests provide isolated JWT decoders, test upstream HTTP servers, and
a closed-port failure target.

## Prerequisites

- Java 21
- Repository Maven wrapper available at the repository root
- Network access only if the Maven wrapper or dependencies are not already cached

Run commands from the repository root.

## 1. Verify focused unit and boundary behavior

```powershell
.\mvnw.cmd -pl services/api-gateway -am `
  -Dtest=GatewayErrorCodeTests,GatewayTraceIdResolverTests,GatewayErrorObservationTests,GatewayHttpErrorWriterTests,GatewayFailureClassifierTests,GatewaySecurityErrorHandlerTests,GatewayWebExceptionHandlerTests `
  test
```

Expected outcomes:

- exactly seven approved status/code/message mappings;
- valid trace preserved and invalid/missing trace generated;
- trace/path control characters are escaped before logging;
- invalid credentials remain 401 while verification infrastructure failure is 503;
- committed response is not rewritten;
- unexpected failure uses a safe 500 body.

## 2. Verify reactive security and routing contracts

```powershell
.\mvnw.cmd -pl services/api-gateway -am `
  -Dtest=ProductAdminGatewayRouteTests,GatewayUnknownPathSecurityTests,GatewayAuthenticationFailureContractTests `
  test
```

Expected outcomes:

- Product Admin keeps its existing 400/401/403 codes and messages;
- every Gateway-owned error contains a non-blank trace ID;
- 401 includes `WWW-Authenticate: Bearer`;
- anonymous unknown path returns 401;
- authenticated unknown path returns generic 403 `ACCESS_DENIED`;
- unknown Actuator paths follow the same deny-by-default rule while configured health remains public;
- JWT/JWKS prerequisite failure returns 503 without leaking provider details.

## 3. Verify downstream ownership and Gateway infrastructure failures

```powershell
.\mvnw.cmd -pl services/api-gateway -am `
  -Dtest=GatewayProxyPassThroughTests,GatewayDownstreamUnavailableTests,GatewayUnexpectedFailureTests `
  test
```

Expected outcomes:

- representative downstream 400, 404, 409, and 500 responses retain exact status and body bytes;
- a selected route with no downstream HTTP response returns 503 `DOWNSTREAM_UNAVAILABLE`;
- a synthetic uncommitted Gateway exception returns safe 500 `GATEWAY_INTERNAL_ERROR`;
- connection addresses, exception messages, tokens, and request-body sentinels do not appear in the
  Gateway body.

## 4. Run the required clean module reactor gate

```powershell
.\mvnw.cmd -pl services/api-gateway -am clean verify
```

Expected result: exit code `0`; all Gateway and required upstream-module tests pass.

Record the command, scope, exit code, and concise result in `validation.md`. A task is not complete
until its required command passes.

## Not Applicable

- Full-repository build: no root/shared production code changes
- Database or Liquibase validation: no persistence change
- Kafka validation: no message contract or consumer/producer change
- Load/concurrency validation: no stateful hot path or approved performance threshold change
- Kubernetes dry-run: no manifest or overlay change
