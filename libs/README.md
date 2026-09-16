# Shared Libraries

Shared libraries are allowed only for stable technical primitives. They must not become a shortcut
for sharing business entities, repositories, or service-owned policies.

| Directory | Status | Purpose |
|---|---|---|
| [`common-web/`](common-web/README.md) | Implemented Maven module | HTTP envelopes and pagination metadata |
| `kafka-support/` | Reserved | Future approved technical Kafka helpers |
| `observability/` | Reserved | Future approved cross-cutting observability helpers |
| `security-jwt/` | Reserved | Future approved security primitives |
| `test-support/` | Reserved | Future approved test-only fixtures/utilities |

Before promoting duplicated code into a library, confirm that it has no domain ownership and update
the governing plan if it adds a production dependency.
