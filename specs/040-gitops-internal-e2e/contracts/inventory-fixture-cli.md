# Inventory Fixture CLI Contract

This is an operator-only contract for a temporary Kubernetes Job using the Inventory service image.
It is not a public HTTP endpoint and is disabled in normal Deployments.

## Activation

```text
flashsale.inventory.fixture.enabled=true
spring.main.web-application-type=none
spring.task.scheduling.enabled=false
LIQUIBASE_ENABLED=false
```

## Inputs

```text
flashsale.inventory.fixture.variant-id   UUID, required
flashsale.inventory.fixture.sku-snapshot nonblank string, required
flashsale.inventory.fixture.quantity    positive integer, required
flashsale.inventory.fixture.reason      bounded nonblank string, required
```

The Job receives the existing `inventory-secrets` and runtime ConfigMap so it connects only to
`inventory_db`. The runner must not receive or print JWT, Stripe, Kafka, Redis, or other service
credentials.

## Behavior

1. Bind and validate all properties before calling the application.
2. Invoke `InitializeInventoryUseCase.initialize(InitializeInventoryCommand)`.
3. Print a bounded success line containing only variant ID, quantity, and result status.
4. Exit zero on success; exit non-zero on validation, duplicate, persistence, or domain failure.

The adapter must not execute SQL, use `JdbcTemplate`, call another service, or delete rows. A
duplicate variant is a failed fixture and must be reported for operator cleanup/retry.
