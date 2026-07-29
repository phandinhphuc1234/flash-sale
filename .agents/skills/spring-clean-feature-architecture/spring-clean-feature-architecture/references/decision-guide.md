# Decision guide

## 1. Choose service-root layers or feature-first packages

### Single stable capability

Use this when the service and its only business capability are effectively the same and no meaningful sibling feature exists:

```text
com.philia.flashsale.order
├── OrderServiceApplication.java
├── domain/
├── application/
├── adapter/
├── websupport/
└── configuration/
```

This avoids redundant names such as `com.philia.flashsale.order.order`.

### One current capability with expected growth

Use an explicit feature package when the service is expected to gain sibling capabilities, the
first feature already has a rich internal structure, or repository-wide navigation consistently
starts with business features:

```text
com.philia.flashsale.order
├── OrderServiceApplication.java
├── order/
│   ├── domain/
│   ├── application/
│   └── adapter/
├── websupport/
├── security/
├── observability/
└── configuration/
```

The repeated word in `order.order` is acceptable here: the first `order` names the deployable
service/bounded context, while the second names the feature/module inside it. Do not use this shape
only for visual symmetry when the service is permanently tiny.

### Multiple capabilities inside one microservice

Use this when the service owns several cohesive business features:

```text
com.philia.flashsale.order
├── order/
│   ├── domain/
│   ├── application/
│   └── adapter/
├── fulfillment/
│   ├── domain/
│   ├── application/
│   └── adapter/
├── returns/
│   ├── domain/
│   ├── application/
│   └── adapter/
├── websupport/
├── security/
├── observability/
└── configuration/
```

Do not create a separate feature for every endpoint. A feature should represent a cohesive capability, lifecycle, aggregate, or policy boundary.
Creating, reading, cancelling, and archiving the same Order remain use cases inside `order`; they do
not become sibling feature packages merely because they have separate endpoints.

Use these boundary tests before creating a feature package:

- it owns a cohesive use-case family or aggregate lifecycle;
- its terminology and rules can be explained without listing HTTP routes;
- it can expose a small application API without exposing persistence internals;
- changes inside it usually move together.

If those tests fail, the candidate is probably a use case or adapter inside an existing feature,
not a new feature.

## 2. Complexity classification

### SIMPLE_CRUD

Signals:

- Lookup/reference data.
- Mostly create/read/update/delete.
- Few or no lifecycle transitions.
- No concurrency, idempotency, or important invariants.
- One input adapter and one persistence adapter.

Recommended shape:

```text
feature/
├── web/
├── application/
└── persistence/
```

Possible simplifications:

- One model may serve application and persistence when Hibernate behavior cannot leak into business logic.
- No inbound port interface when there is only one caller and no useful test boundary.
- No domain service or policy without actual business rules.

### MODERATE

Signals:

- Several validation/business rules.
- Multiple use cases.
- Remote service or messaging dependency.
- Need to test application orchestration independently.
- Persistence mapping is non-trivial.

Recommended shape:

```text
feature/
├── domain/
├── application/
│   ├── port/in/
│   ├── port/out/
│   └── usecase/
└── adapter/
    ├── in/
    └── out/
```

### CORE_DOMAIN

Signals:

- Aggregate invariants and state transitions.
- Flash-sale concurrency or oversell prevention.
- Idempotency and duplicate-request semantics.
- Reservation expiry, compensation, or outbox behavior.
- Multiple representations: HTTP, domain, database, Kafka.
- Long-term maintenance and architectural enforcement matter.

Use full separation, separate JPA models, explicit ports, domain events, test slices, integration tests, and ArchUnit.

## 3. When to create a port

Create an outbound port when application logic needs a capability whose implementation is an external detail:

- Load/save state.
- Publish an event.
- Call inventory, payment, or another external service.
- Read time, generate identifiers, acquire a lock, or access a cache when deterministic testing matters.

Do not create a port solely to wrap a pure in-memory helper or because every class “must have an interface.”

Create an inbound port when:

- Multiple inbound adapters invoke the same use case.
- The use case is a stable public application API.
- It improves testability or module boundaries.

An application service can be the concrete use-case API for a small feature when an interface adds no value.

## 4. Separate domain and JPA models when

- Lazy loading or proxies could leak.
- Table structure differs from aggregate structure.
- Persistence requires surrogate fields, joins, audit columns, or technical statuses.
- Domain constructors/invariants must remain controlled.
- Multiple storage technologies or representations exist.

A simple CRUD feature may use one model, but acknowledge the trade-off and keep JPA annotations from driving business behavior.

## 5. Decide whether to create nested packages

Read `feature-internal-organization.md` before expanding a feature into `model`, `policy`,
`exception`, `request`, `mapper`, `jpa`, and similar packages. Create a subpackage because it groups
multiple related responsibilities or protects a boundary, not because it appears in the template.

## 6. Decide service-wide technical packages

- Prefer the repository's established service-wide packages such as `websupport`, `security`,
  `observability`, and `configuration`. Use a `shared` wrapper only when that is already the local
  convention and it contains technical concerns used by multiple features.
- Keep feature-specific errors, mappers, policies, statuses, and business services inside the
  owning feature.
- Use `bootstrap` only when explicit bean/module assembly improves composition. If component scanning
  and `configuration` already make wiring clear, omit `bootstrap`.
- When the monorepo already provides a stable technical library such as `libs/common-web`, import its
  envelope types instead of recreating them under every service.

## 7. Decide state-plus-event persistence

When a durable mutation must publish a Kafka event, do not let the use case perform an unrelated
database save followed by a direct broker send. Prefer an atomic capability such as:

```text
PersistOrderWithOutboxPort
```

Its persistence adapter commits aggregate state and the outbox record in one local transaction. A
separate outbound publisher later delivers the approved integration event. Keep `SaveOrderPort` and
`PublishOrderEventPort` separate only when the governing plan explicitly accepts non-atomic delivery
or the event is not required after commit.
