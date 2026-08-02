# Data Model: HTTP Response Standardization

This feature changes no durable data, tables, migrations, JPA entities, or domain aggregates.

## Transport types

- `ApiResponse<T>`: success flag, stable success code, message, payload, UTC timestamp.
- `ApiErrorResponse`: false success flag, service-owned error code, safe message, optional field violations, UTC timestamp.
- `FieldViolation`: request field and safe validation message.
- `PageResponse<T>`: page items and `PageMeta` inside the top-level success `data` field.

## Invariants

- Transport envelopes do not own business error semantics.
- No persistence or domain type crosses the HTTP boundary.
- Trace correlation is carried by the approved HTTP header contract, not by a new domain field.
