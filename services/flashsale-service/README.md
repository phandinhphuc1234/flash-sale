# Flash Sale Service

Flash Sale Service owns high-contention reservation admission and the reservation lifecycle. Redis
provides atomic hot-path decisions; PostgreSQL remains durable truth.

## Seckill hot path

```text
Campaign lifecycle -> projection -> Redis campaign state
POST reservation   -> Redis Lua checks active window, stock, per-user quota, idempotency
accepted result     -> Redis Stream handoff
stream consumer     -> PostgreSQL reservation journal + outbox
outbox              -> PurchaseAcceptedV1
```

Lua executes the decision atomically so concurrent requests cannot oversell the Redis allocation.
The Redis Stream bridges the failure window between the fast decision and durable PostgreSQL
persistence. Reconciliation and reclaim workers resume abandoned work.

## Boundaries

- shopper: create/read owned reservations under `/api/v1/flash-sales/**`;
- inbound Kafka: Campaign lifecycle and Order confirm/release commands;
- outbound Kafka: accepted, confirmed, and released purchase events;
- recovery HTTP: Campaign snapshot through an authenticated Feign client.

## Correctness rules

- An idempotency replay returns the original logical reservation.
- Confirmation and release are idempotent and correlated to the matching causation ID.
- Payment failure/deadline releases stock; success confirms it.
- Redis is not a substitute for the journal/outbox, and operators must not repair business state by
  editing Redis keys manually.

## Configuration and verification

Runtime groups cover Redis/Stream tuning, PostgreSQL/Liquibase, JWT/service credentials, campaign
recovery, reservation TTL and idempotency retention, Kafka/Schema Registry, topic/group/DLT, outbox,
and reconciliation switches.

```powershell
.\mvnw.cmd -pl services/flashsale-service -am verify
pwsh -NoLogo -NoProfile -File .\infra\docker\smoke\feature-044-purchase-saga.ps1 -Scenario All
```

The second command is a long cross-service scenario; follow its prerequisites and timeout guard.

## Troubleshooting

- Reservation waits without an Order: trace Redis Stream -> journal/outbox -> Kafka consumer.
- Duplicate/constraint failure after release: inspect causation-specific outbox identity; do not
  reuse a single event identity for distinct confirm/release outcomes.
- Consumer poison record: inspect the owned DLT and Schema Registry subject before replay.
