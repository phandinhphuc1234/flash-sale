# Reviewer Contract: Cart Service Scaffold

This is a repository scaffold contract, not a business HTTP, OpenAPI, AsyncAPI, or Kafka contract.

## Service Identity

| Property | Required value |
|----------|----------------|
| Maven module | `services/cart-service` |
| Artifact and application name | `cart-service` |
| Java package | `com.philia.flashsale.cart` |
| Compose/Kubernetes DNS name | `cart-service` |
| Internal port | `8080` |
| Development host port | `18089` |
| Future owned logical database | `cart_db` |

## Operational Endpoint Contract

| Endpoint | Purpose | Configuration source |
|----------|---------|----------------------|
| `/actuator/health` | Aggregate application health | Spring Boot Actuator auto-configuration |
| `/actuator/health/liveness` | Process liveness probe | Declarative health-probe configuration |
| `/actuator/health/readiness` | Traffic readiness probe | Declarative health-probe configuration |
| `/actuator/info` | Basic application identity | Declarative info configuration |
| `/actuator/prometheus` | Prometheus-compatible metrics | Runtime registry plus declarative endpoint exposure |

The service must not construct a Prometheus registry in Java code.

## Base and Development Exposure

- Base Compose exposes port `8080` only to the internal network.
- Development Compose optionally maps `${CART_SERVICE_PORT:-18089}:8080`.
- No API Gateway route exists in this feature.
- The debug mapping does not establish a public Cart business API.

## Exact Source Allowlist

The only Java source files approved by this feature are:

```text
services/cart-service/src/main/java/com/philia/flashsale/cart/CartServiceApplication.java
services/cart-service/src/test/java/com/philia/flashsale/cart/CartServiceApplicationTests.java
```

All architecture leaf directories contain only empty `.gitkeep` markers.

## Prohibited Contract Surface

This feature defines no:

- Cart URI or HTTP method;
- request or response body;
- authentication or authorization rule;
- synchronous service call;
- Kafka topic, key, schema, or event version;
- Cart table or field;
- Redis key, TTL, or Lua operation;
- checkout, inventory, pricing, or expiration behavior.

Any such contract requires a later approved specification, plan, contract file, tests, and tasks.
