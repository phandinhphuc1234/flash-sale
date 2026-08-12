# ADR 0010: Rename Chatting Service to Inventory Service

## Status

Accepted — 2026-07-27

## Context

The repository scaffold previously contained a `chatting-service`, but the next planned
capability is inventory ownership. Chat functionality is out of scope for the current project
phase. Keeping the old name would make Maven modules, container DNS names, and deployment plans
misleading.

## Decision

Rename the scaffold to `inventory-service` while preserving its current behavior and Clean/Hexagonal
package layout. The service owns only its application module, runtime configuration, tests, and
future inventory migrations. No inventory business logic, API, database schema, or messaging
contract is introduced by this rename.

The service is exposed locally as `inventory-service` on the existing development debug port
`18088`. Historical Spec Kit artifacts retain their original `chatting-service` wording because
they describe the completed scaffold that existed at the time they were approved.

## Consequences

- Maven, Docker Compose, local environment examples, Java packages, and current architecture
  documentation use `inventory-service`.
- Existing clients must use the new service/DNS/image name when inventory integration is added.
- A future inventory feature must introduce its own approved Spec Kit artifacts and API/data
  contracts before production behavior is implemented.
