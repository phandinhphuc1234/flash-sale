# Package templates

## Contents

- Full feature template
- Detailed Order example
- Simple CRUD template
- Test template

## Full feature template

```text
<base-package>/
├── <feature>/
│   ├── domain/
│   │   ├── model/
│   │   ├── event/
│   │   ├── exception/
│   │   ├── policy/
│   │   └── service/
│   │
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   └── out/
│   │   ├── usecase/
│   │   ├── command/
│   │   ├── query/
│   │   ├── result/
│   │   └── exception/
│   │
│   └── adapter/
│       ├── in/
│       │   ├── web/
│       │   │   ├── request/
│       │   │   ├── response/
│       │   │   ├── mapper/
│       │   │   └── error/
│       │   └── messaging/
│       │
│       └── out/
│           ├── persistence/
│           │   └── jpa/
│           │       ├── entity/
│           │       ├── repository/
│           │       ├── mapper/
│           │       └── <Feature>PersistenceAdapter.java
│           ├── messaging/
│           │   └── kafka/
│           └── client/
│               └── <remote-capability>/
│
├── websupport/                    # service-wide HTTP context/error translation, when needed
├── security/                      # service-wide framework security, when needed
├── observability/                 # service-wide tracing/logging/metrics, when needed
├── configuration/                 # established Spring wiring package
└── bootstrap/                     # optional; omit when configuration is sufficient
```

Create only folders that contain real code.

## Detailed Order example

```text
com.philia.flashsale.order
├── OrderServiceApplication.java
├── order/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Order.java
│   │   │   ├── OrderId.java
│   │   │   ├── OrderItem.java
│   │   │   ├── OrderStatus.java
│   │   │   └── MoneyVnd.java
│   │   ├── event/
│   │   │   ├── OrderCreated.java
│   │   │   └── OrderCancelled.java
│   │   ├── exception/
│   │   │   ├── InvalidOrderStateException.java
│   │   │   └── InvalidOrderItemException.java
│   │   ├── policy/
│   │   │   └── OrderCancellationPolicy.java
│   │   └── service/
│   │       └── OrderPricingService.java
│   ├── application/
│   │   ├── port/in/
│   │   │   ├── CreateOrderUseCase.java
│   │   │   ├── CancelOrderUseCase.java
│   │   │   └── GetOrderUseCase.java
│   │   ├── port/out/
│   │   │   ├── LoadOrderPort.java
│   │   │   ├── ReserveStockPort.java
│   │   │   └── PersistOrderWithOutboxPort.java
│   │   ├── usecase/
│   │   │   ├── CreateOrderService.java
│   │   │   ├── CancelOrderService.java
│   │   │   └── GetOrderService.java
│   │   ├── command/                  # transport-independent write intent
│   │   ├── query/                    # transport-independent read intent, when needed
│   │   ├── result/                   # application-owned output
│   │   └── exception/                # use-case failure, not HTTP failure
│   └── adapter/
│       ├── in/web/
│       │   ├── OrderController.java
│       │   ├── request/              # Bean Validation and HTTP request shape
│       │   ├── response/             # JSON response shape
│       │   ├── mapper/               # request -> command; result -> response
│       │   └── error/                # feature API codes and web translation
│       ├── in/messaging/             # consumers/listeners and wire DTO mappers
│       └── out/
│           ├── persistence/jpa/
│           │   ├── entity/           # Order, OrderItem, and approved outbox JPA types
│           │   ├── repository/       # Spring Data repositories stay internal
│           │   ├── mapper/           # domain/persistence and outbox mapping
│           │   └── OrderPersistenceAdapter.java
│           ├── messaging/kafka/      # publisher for approved versioned outbox records
│           └── client/inventory/
├── websupport/error/                 # service-wide HTTP formatter/handler
├── security/                         # JWT/framework security boundary, when needed
├── observability/                    # trace/log/metric boundary support
└── configuration/                    # Spring wiring and explicit assembly
```

The example is a menu, not a checklist. Keep `order/` because the service is expected to grow and
the feature has enough internal responsibilities to justify an explicit module. Omit any nested
package until the first real class belongs there.

If the monorepo already owns `ApiResponse`, `ApiErrorResponse`, `FieldViolation`, or pagination
envelopes in a stable technical library, do not duplicate them inside the service. Keep only the
service-specific global handler, trace extraction, and error mapping locally.

`OrderPricingService` belongs in `domain.service` only when pricing is pure domain behavior over
domain inputs. If pricing needs a campaign service, database, or cache, keep the orchestration in an
application use case behind an output port.

`PersistOrderWithOutboxPort` represents required atomic state-plus-event persistence. The Kafka
publisher remains an outbound adapter that drains approved outbox records; the use case must not
perform an unrelated save followed by a direct required publish.

## Simple CRUD template

```text
<feature>/
├── web/
│   ├── <Feature>Controller.java
│   ├── request/
│   ├── response/
│   └── mapper/
├── application/
│   ├── <Feature>ApplicationService.java
│   └── exception/
└── persistence/
    ├── <Feature>JpaEntity.java
    ├── <Feature>JpaRepository.java
    └── <Feature>PersistenceMapper.java
```

Even in this simplified shape, web DTOs must not become persistence or domain contracts, and controllers must not own business decisions.

## Test template

```text
src/test/java/<base-package>/
├── <feature>/
│   ├── domain/
│   ├── application/
│   └── adapter/
│       ├── in/web/
│       └── out/persistence/
├── architecture/
└── integration/
```
