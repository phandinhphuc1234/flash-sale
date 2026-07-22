# Data Model: Clean Hexagonal Service Scaffold

This feature introduces no business data model.

## Scaffold Concepts

- **Domain Zone**: Future home for service-owned models, value objects, policies, events, and exceptions.
- **Application Zone**: Future home for commands, results, use cases, and ports.
- **Adapter Zone**: Future home for inbound and outbound technical integration.
- **Configuration Zone**: Future home for Spring wiring that connects adapters and application components.

## Explicit Non-Entities

- No JPA entity is introduced.
- No shared domain model is introduced.
- No database schema or migration is introduced.
- No Kafka event payload is introduced.
- No Redis Lua script is introduced.
