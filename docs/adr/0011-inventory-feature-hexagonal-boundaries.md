# ADR 0011: Inventory feature-local Hexagonal boundaries

## Status

Accepted

## Context

Inventory contains multiple business capabilities with real invariants: physical stock, campaign
allocation lifecycle, and immutable movements. A flat package grouped by feature is useful for
navigation, but hiding all driving and driven adapters at the same level makes the dependency
boundaries unclear. In particular, persistence is a driven (`out`) adapter and HTTP controllers are
driving (`in`) adapters.

## Decision

Each inventory feature uses feature-local Clean/Hexagonal boundaries only when the responsibility
exists:

```text
inventory/
├── stock/
│   ├── domain/
│   ├── application/
│   └── adapter/
│       ├── in/web/
│       └── out/persistence/
├── allocation/
│   ├── domain/
│   ├── application/
│   └── adapter/
│       ├── in/web/
│       └── out/persistence/
├── movement/
│   ├── domain/
│   ├── application/
│   └── adapter/out/persistence/
├── outbox/
│   └── adapter/out/persistence/
├── websupport/
└── configuration/
```

`domain` is created only for business invariants and models. `application` contains use-case
orchestration. `adapter/in` contains HTTP or messaging entry points; `adapter/out` contains JPA,
Kafka, Redis, or external-client integrations. `websupport` is reserved for cross-feature HTTP error
translation and request context. Empty placeholder packages are not created.

## Consequences

- Developers can navigate from a feature to its domain, use cases, and adapters without a global
  technical dumping ground.
- Stock and allocation controllers are driving adapters, while JPA persistence remains driven
  adapters.
- Movement and outbox do not receive empty `adapter/in` or `domain` packages when no such responsibility
  exists yet.
- Package moves do not change HTTP contracts or business behavior.

## References

- [Service Clean/Hexagonal Structure](../architecture/service-clean-hex-structure.md)
- [Feature 016 plan](../../specs/016-inventory-service/plan.md)
