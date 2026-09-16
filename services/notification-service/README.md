# Notification Service

`notification-service` is a reserved Spring Boot boundary, not a completed notification product.
It currently supplies an application scaffold and health/metrics runtime only.

## Current status

- no supported public or internal business HTTP endpoint;
- no approved email, SMS, push, or template domain;
- no Kafka consumer contract;
- no owned business database schema;
- not part of the active cloud image-delivery set.

This explicit status prevents architecture diagrams and frontend code from depending on behavior
that does not exist.

## Future change rule

Before implementation, approve a feature specification covering notification ownership, consent,
PII retention/redaction, provider retry semantics, deduplication identity, templates/localization,
Kafka contracts, and operational failure handling. Do not turn Order events into an implicit email
contract without versioning and consumer idempotency.

## Verify scaffold

```powershell
.\mvnw.cmd -pl services/notification-service -am verify
```
