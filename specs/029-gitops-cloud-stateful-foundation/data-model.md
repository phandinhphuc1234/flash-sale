# Data Model: Cloud Stateful Platform

This feature adds infrastructure resources, not service-owned business entities.

## Resource inventory

| Resource | Identity | Durable material | Internal endpoint |
|---|---|---|---|
| PostgreSQL | StatefulSet `postgres` | One `gp2` PVC, 20Gi | `postgres:5432` |
| Redis | StatefulSet `redis` | One `gp2` PVC, 4Gi, AOF | `redis:6379` |
| Kafka | StatefulSet `kafka` | One `gp2` PVC, 8Gi, KRaft log | `kafka:9092` |
| Schema Registry | Deployment `schema-registry` | Kafka `_schemas` topic | `schema-registry:8081` |

## PostgreSQL logical databases

The bootstrap ConfigMap creates these empty databases on a new volume:

`auth_db`, `product_db`, `campaign_db`, `flashsale_db`, `inventory_db`, `order_db`, `payment_db`.

The services own their Liquibase migrations and may not query another service's database.

## Secret reference contract

The stateful manifests reference Kubernetes Secret `flash-sale-secrets`:

- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `REDIS_PASSWORD`

The values are intentionally absent from this repository and are provisioned in Phase 15.

## Invariants

- Every stateful workload has exactly one replica in this demo phase.
- Platform Services are internal only.
- Redis remains non-authoritative for business data.
