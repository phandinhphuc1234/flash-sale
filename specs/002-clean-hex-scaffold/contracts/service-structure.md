# Contract: Service Structure Convention

This contract is a reviewer-facing convention for this scaffold feature. It is not a public HTTP or Kafka contract.

## Required Zones

Each service module must keep source code in service-owned packages under:

```text
services/<service>/src/main/java/com/philia/flashsale/<service-package>/
```

The allowed architectural zones are:

- `domain`
- `application`
- `adapter`
- `configuration`

## Dependency Direction

Allowed dependency direction:

```text
adapter -> application -> domain
configuration -> adapter + application
```

Prohibited dependency direction:

```text
domain -> application
domain -> adapter
domain -> configuration
application -> adapter
application -> configuration
adapter -> configuration
```

## Port Naming Rule

Ports describe business capabilities, not implementation APIs.

Allowed examples:

- `LoadActiveSalePort`
- `ReserveQuotaPort`
- `PersistReservationWithOutboxPort`
- `PublishReservationEventPort`
- `SendNotificationPort`

Rejected examples:

- `RedisGetPort`
- `RedisSetPort`
- `KafkaSendPort`
- `JpaSavePort`
- `HttpPostPort`

## Scaffold Boundary

This feature must not add:

- Controllers
- Service implementation classes
- JPA entities or repositories
- Concrete port interfaces
- Kafka producers or consumers
- Redis scripts
- Database migrations
- Business HTTP or Kafka contracts
- Shared business libraries
