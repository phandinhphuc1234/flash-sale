# Common Web Library

`common-web` is a deliberately small technical library for consistent HTTP response shapes. It
contains success/error envelopes, field violations, and pagination metadata.

It does **not** contain domain entities, service DTOs, security policy, persistence types, Kafka
records, or business exceptions. Each service owns those concerns and maps them at its web adapter.

## Main types

- `ApiResponse<T>` — successful response envelope;
- `ApiErrorResponse` — safe error envelope;
- `FieldViolation` — validation detail without implementation leakage;
- `PageMeta` and `PageResponse<T>` — shared pagination shape.

## Build

```powershell
.\mvnw.cmd -pl libs/common-web -am verify
```

Changing an envelope is an API contract change. Update the governing feature and HTTP documentation,
then verify every affected service/security failure writer before merging.
