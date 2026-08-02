---
name: standardize-api-response-error-handling
description: Standardize and safely migrate HTTP success/error envelopes, validation failures, exception-to-status mappings, security failures, trace headers, and pagination responses in this Flash Sale Java 21 / Spring Boot 3 monorepo. Use when adding or changing controllers, web DTOs, REST controller advice, Spring Security failure handlers, WebFlux Gateway error writers, or public HTTP contracts.
---

# Standardize API Response and Error Handling

Use the repository's existing `libs/common-web` contract as the target for HTTP responses. Keep
HTTP transport concerns in web adapters; keep business error meaning in the bounded context that
owns it.

Read [project-http-contract.md](references/project-http-contract.md) before changing a contract.

## Required workflow

1. Before a production contract change, read the constitution and the active feature's approved
   `spec.md`, `plan.md`, `tasks.md`, and HTTP contract. Treat a JSON-body change as observable API
   behavior: update and approve artifacts before implementation.
2. Identify the response owner. A service returns its own success/error response; the Gateway only
   renders Gateway-owned failures and passes an already-started downstream response through.
3. Select the correct boundary:
   - Servlet services use controllers plus `@RestControllerAdvice`.
   - Security filter failures use an `AuthenticationEntryPoint` and `AccessDeniedHandler`, not MVC
     advice alone.
   - The WebFlux Gateway uses its reactive error writer/handlers, not servlet classes.
4. Use the shared envelope types. Do not add an endpoint-specific `*ApiResponse`,
   `*ErrorResponse`, or duplicate pagination record merely to reproduce the same generic shape.
5. Add or adjust contract tests for the response body, HTTP status, required headers, and safe
   failure behavior. Run the affected Maven module verification before completing the task.

## Contract rules

- Use `ApiResponse<T>` for a successful JSON body and `ApiErrorResponse` for a failed JSON body.
  `PageResponse<T>` is payload data inside `ApiResponse`, never a replacement top-level envelope.
- Return `ResponseEntity` whenever status, `Location`, `Retry-After`, `Cache-Control`, or another
  response header matters. A direct `ApiResponse<T>` is acceptable only for a fixed `200 OK` with
  no response-header decision.
- Keep the current trace convention: propagate and echo `X-Trace-Id` at HTTP boundaries. The
  current shared envelopes intentionally do not contain `traceId`; do not add response metadata or
  replace this header convention without an approved, versioned contract migration.
- Map `2xx` only to success, `4xx` to caller/request/resource-state failures, and `5xx` to safe
  service or dependency failures. Never return `200 OK` for a failed operation.
- Return `204 No Content` with an empty body. Use `200`, `201`, or `202` when an envelope is needed.
- Use an owning service's stable error codes. Do not put business error enums, business exceptions,
  JPA entities, or domain DTOs in `libs/common-web`.
- Never expose a stack trace, SQL/database detail, secret, token, cookie, internal class name, or
  uncontrolled exception message. Log the cause internally with the trace ID.
- Do not catch expected business exceptions in controllers. Translate them centrally in a
  feature-scoped or service-wide web error handler.

## Boundary design

Keep request/response DTOs and web mappers in the owning feature's inbound web adapter. A small
service-wide `websupport/error` package is appropriate for framework/security concerns shared by
multiple features. Error code ownership stays with the feature or bounded context.

Prefer domain/application exceptions that carry a stable reason or error code but no Spring
`HttpStatus`. Map that reason to the HTTP status in the inbound web adapter. Preserve existing
published behavior while migrating; do not rename error codes as incidental cleanup.

For Bean Validation, map field errors to shared `FieldViolation(field, message)`. The current type
has no per-field code, so do not invent one in individual services.

## Special HTTP failures

- `401`: unauthenticated or invalid credentials/token; render through the security entry point.
- `403`: authenticated but not allowed; render through the denied handler. Do not confuse it with
  `401`.
- `409`: a documented resource-state conflict, optimistic-version conflict, or incompatible
  idempotency replay.
- `422`: use only if an approved API contract explicitly distinguishes semantic invalidity from
  `400`; otherwise use the existing bounded-context mapping.
- `429`: include `Retry-After` when a retry time is known, plus the approved rate-limit headers.
- `503`: a safe, retryable dependency/service failure. Do not substitute it for an application
  validation or business conflict.

## Migration discipline

The repository still contains legacy service-specific wrappers and handlers. Do not remove or
silently reshape them in an unrelated feature. Migrate one service or one published endpoint group
at a time:

1. Record the before/after JSON body, statuses, headers, and compatibility impact in its feature
   contract.
2. Migrate success, validation, business, security, and unexpected-error paths together.
3. Replace duplicate envelope records only after all references and contract tests use
   `common-web`.
4. Update API documentation and route/gateway tests when the public contract changes.

## Completion checklist

- [ ] No JPA entity or domain object crosses the HTTP boundary.
- [ ] Success and error paths use the shared shape or an explicitly approved legacy compatibility
      path.
- [ ] Status and error code are independently correct.
- [ ] Validation errors use `FieldViolation`.
- [ ] `X-Trace-Id` is propagated and returned according to the endpoint's established contract.
- [ ] Security/filter failures use the proper non-controller handler.
- [ ] `429`, `201`, `202`, and `204` semantics are correct where applicable.
- [ ] Tests prove body, headers, status, and no sensitive-detail leakage.

