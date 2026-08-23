# Public Gateway Smoke Contract

The smoke consumes existing HTTP routes; it does not introduce a new application API.

| Check | Request | Expected result |
|---|---|---|
| Readiness | `GET /actuator/health/readiness` | HTTP `200` |
| Public catalog | Existing anonymous catalog route through `api-gateway` | HTTP `200` |
| Admin boundary | Existing admin route without bearer token | HTTP `401` or `403`; no data body is accepted |

The runner discovers the host from the Kubernetes `api-gateway` Service, calls the Gateway Service
port `8080`, and uses bounded DNS/HTTP timeouts. It must not print Authorization headers or any
Secret value.
