# Data Model: Cloud Gateway Smoke

This phase introduces no durable data model, database table, Kafka event, or Kubernetes Secret.

| Observation | Source | Expected result |
|---|---|---|
| Gateway readiness | GET /actuator/health/readiness | HTTP 200 |
| Public catalog route | GET /api/v1/catalog/products?page=0&size=1 | HTTP 200 |
| Protected admin route | GET /api/v1/admin/catalog/products?page=0&size=1 without token | HTTP 401 or 403 |
