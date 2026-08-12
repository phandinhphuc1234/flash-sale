# Operational Endpoint Contract

## Scope

Every one of the nine service skeletons exposes the same framework-managed operational interface on
its application port. This is an operations contract only; it is not a business API and creates no
service-to-service dependency.

## Endpoints

| Method | Path | Expected purpose |
|--------|------|------------------|
| `GET` | `/actuator/health` | Aggregate application health |
| `GET` | `/actuator/info` | Framework-managed application information |
| `GET` | `/actuator/health/liveness` | Liveness probe state |
| `GET` | `/actuator/health/readiness` | Readiness probe state |
| `GET` | `/actuator/prometheus` | Prometheus-compatible scrape payload |

## Acceptance behavior

- A normally started skeleton returns HTTP 200 for all five endpoints.
- Health and probe responses use the framework Actuator JSON representation and report `UP` for a
  normally started service.
- The information response contains `app.name` equal to the module's `spring.application.name`.
- The Prometheus endpoint returns a non-empty text payload containing framework/JVM metrics in a
  Prometheus-compatible format.
- A service reaches healthy liveness and readiness state within 60 seconds on the supported
  development environment.
- The endpoint paths are identical across services; the port is selected by `SERVER_PORT` and defaults
  to `8080`.
- No custom endpoint implementation, registry bean, controller, authentication policy, or route is
  introduced by this feature.
