# Phase 23 Data Model

This feature does not add business tables, Kafka records, Redis keys, or service-owned entities.

| Operational object | Owner | Lifecycle | Sensitive fields |
|---|---|---|---|
| `api-gateway` public Service | Kubernetes/cloud overlay | Created by Argo after the approved commit; removed by rollback | AWS hostname is non-secret dev metadata |
| Endpoint smoke evidence | Git/spec validation artifact | One record per verification run | Must exclude tokens, passwords, cookies, Secret values and `.env` values |

No service database or durable business state is changed by public exposure or rollback.
