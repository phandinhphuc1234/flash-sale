# Data Model: Clean Architecture Working Baseline

This feature introduces no business data, database entity, schema, API model, or event payload.

## Architecture Concepts

- **Command**: Future application input describing an intent to change state.
- **Query**: Future application input describing a read request without prescribing HTTP or persistence technology.
- **Result**: Future application output independent of web, Kafka, or persistence representations.
- **Boundary representation**: Future adapter-owned DTO, event payload, provider payload, or persistence entity.
- **Mapper**: Future boundary-owned translation between an adapter representation and an inward-facing application or domain type.
- **Marker**: An empty version-control file preserving an approved package location before a real type exists.

## Explicit Non-Entities

- No Product, Campaign, Reservation, Order, Payment, Notification, Chat, or User model is defined.
- No JPA entity, repository, projection, Redis record, or outbox row is defined.
- No request/response DTO or Kafka message is defined.
- No domain invariant or lifecycle is approved by this feature.

