# Authentication service guide

This folder explains the current Feature 015 Authentication service without changing its runtime
behavior. It is intended as a learning guide for the DDD, Clean Architecture, and Hexagonal
boundaries used by the service.

## Read in this order

1. [Class responsibilities](01-class-responsibilities.md) — what each type owns and what it must
   not own.
2. [Authentication flows](02-authentication-flows.md) — the request path and the sequence diagrams
   for register, login, refresh, logout, and cleanup.
3. [Boundary rules](03-boundary-rules.md) — a short checklist for adding the next authentication
   capability safely.
4. [Full service structure](04-authentication-service-structure.md) — the complete source,
   resource, test, and dependency tree.

The structure document also contains the recommended feature-first target for future Authentication
work. It is intentionally a navigation recommendation; the current Feature 015 implementation is
not moved without a separate approved refactor.

## The dependency direction

```text
HTTP / Scheduler
      |
      v
adapter/in  ---> application use case ---> domain
      ^                 |
      |                 v
adapter/out <--- application output ports

configuration wires Spring beans and does not become business logic.
```

The important idea is that a login use case does not know about Spring MVC, JPA, Redis, cookies,
or Nimbus. Those technologies are replaceable adapters around the application and domain core.
