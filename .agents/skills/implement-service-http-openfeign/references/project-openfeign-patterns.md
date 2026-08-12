# Project OpenFeign Patterns

## Contents

1. Repository baseline
2. Technology selection
3. Package and dependency pattern
4. Feign interface and adapter pattern
5. Configuration pattern
6. OAuth2 service identity
7. Tracing and logging
8. Error and retry rules
9. Testing matrix
10. Review anti-patterns

## 1. Repository baseline

- Java 21 and Spring Boot 3.5.x.
- Spring Cloud dependencies are managed by the root Maven BOM.
- Services use package-by-feature with pragmatic Clean/Hexagonal boundaries.
- Public traffic enters through `api-gateway`.
- Docker Compose and Kubernetes Service DNS provide discovery; Eureka is prohibited.
- Synchronous service communication uses documented HTTP/JSON contracts.
- Kafka owns asynchronous workflow/event communication.
- Redis Lua owns only the atomic purchase reservation hot path.
- Micrometer Observation/OpenTelemetry is the tracing direction; current HTTP contracts may also
  require `X-Trace-Id`.

Feature 017 selects OpenFeign only for these control-plane calls:

```text
Campaign Service -> Product Service: validate a campaign Variant
Campaign Service -> Inventory Service: allocate campaign stock idempotently
```

Inventory release remains a future capability and must not be implemented from the existing scope
registration alone.

## 2. Technology selection

Choose OpenFeign when all are true:

- the caller needs an immediate response;
- an approved HTTP contract exists;
- a blocking interaction fits the caller;
- the synchronous dependency chain remains short;
- timeout, authentication, errors, and idempotency are defined.

Do not choose OpenFeign for:

- Gateway reverse proxy routing;
- WebFlux event-loop work;
- Flash Sale -> Order/Payment workflow;
- domain events or fan-out;
- calls inside a per-item loop when a batch contract is possible;
- direct database access disguised as a service client.

Virtual threads reduce the cost of blocked threads but do not remove network latency, downstream
capacity limits, connection limits, timeouts, or cascading failure.

## 3. Package and dependency pattern

The calling service owns the dependency:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

Do not add an explicit version when the root Spring Cloud BOM manages it. Do not add
`spring-cloud-starter-circuitbreaker-resilience4j` automatically; Feature 017 currently defers that
dependency.

Use a business-capability port and a technology-specific adapter:

```text
campaign/application/port/out/ValidateCampaignVariantPort.java
campaign/adapter/out/client/product/
├── ProductFeignClient.java
├── ProductValidationClientAdapter.java
├── dto/ProductValidationRequest.java
├── dto/ProductValidationResponse.java
├── mapper/ProductValidationClientMapper.java
└── error/ProductFeignErrorDecoder.java
```

Collapse `dto`, `mapper`, or `error` into the client package when only one or two small types exist.
Create subpackages with real responsibilities, not to reproduce a diagram.

The port must not mention HTTP or Feign:

```java
public interface ValidateCampaignVariantPort {
    ValidatedCampaignVariant validate(UUID variantId, String traceId);
}
```

If trace identity is already available through an application request context, do not add a
transport header parameter to every business port merely for Feign.

## 4. Feign interface and adapter pattern

Keep the interface narrow and contract-exact:

```java
@FeignClient(
        name = "campaign-product",
        contextId = "campaignProductValidationClient",
        url = "${flashsale.campaign.downstream.product.base-url}",
        configuration = ProductFeignConfiguration.class)
public interface ProductFeignClient {

    @PostMapping("/internal/v1/catalog/variants/campaign-validation")
    ProductValidationResponse validate(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Trace-Id") String traceId,
            @RequestBody ProductValidationRequest request);
}
```

Prefer a request interceptor for cross-cutting service-token/trace headers when it can be scoped to
one client without leaking Product scopes into Inventory calls. Explicit method headers are
acceptable when the contract test benefits from visible inputs and the adapter owns their creation.

Wrap Feign behind an adapter:

```java
@Component
final class ProductValidationClientAdapter implements ValidateCampaignVariantPort {
    private final ProductFeignClient client;
    private final ProductValidationClientMapper mapper;

    @Override
    public ValidatedCampaignVariant validate(UUID variantId, String traceId) {
        ProductValidationResponse response = client.validate(
                serviceAuthorization(), traceId, new ProductValidationRequest(variantId));
        verifyVariantIdentity(variantId, response);
        return mapper.toResult(response);
    }
}
```

The snippet illustrates placement only. Obtain authorization through the approved token manager;
do not construct or log credentials inside the adapter.

## 5. Configuration pattern

Enable only the intended client packages or marker classes:

```java
@EnableFeignClients(basePackageClasses = {
        ProductFeignClient.class,
        InventoryFeignClient.class
})
```

Keep service URLs in service-owned runtime configuration. Feature 017 already uses:

```yaml
flashsale:
  campaign:
    downstream:
      product:
        base-url: ${PRODUCT_SERVICE_URL:http://product-service:8080}
      inventory:
        base-url: ${INVENTORY_SERVICE_URL:http://inventory-service:8080}
```

Add client-specific timeout values only after the active plan approves exact budgets:

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          campaign-product:
            connectTimeout: ${CAMPAIGN_PRODUCT_CONNECT_TIMEOUT_MS:<approved-ms>}
            readTimeout: ${CAMPAIGN_PRODUCT_READ_TIMEOUT_MS:<approved-ms>}
            loggerLevel: basic
          campaign-inventory:
            connectTimeout: ${CAMPAIGN_INVENTORY_CONNECT_TIMEOUT_MS:<approved-ms>}
            readTimeout: ${CAMPAIGN_INVENTORY_READ_TIMEOUT_MS:<approved-ms>}
            loggerLevel: basic
```

`<approved-ms>` is a documentation placeholder, not a runtime default. Never invent timeout values
while implementing a task. Keep Feign logging at `NONE` or `BASIC` in production; never use `FULL`
where authorization headers, cookies, or sensitive bodies may appear.

Do not annotate a client-specific Feign configuration so broadly that component scanning applies
its decoder/interceptors to every client. Use distinct `contextId` values and prove isolation in
tests.

## 6. OAuth2 service identity

Feature 017 uses two Campaign registrations with one machine client and different requested scopes:

```text
campaign-product              -> catalog.read
campaign-inventory-allocation -> inventory.campaign.allocate
```

Use `AuthorizedClientServiceOAuth2AuthorizedClientManager` through the approved
`CampaignServiceTokenManager`. Cache tokens only in memory and renew before expiry. Do not persist
or log access tokens or the Campaign client secret.

For each outbound call:

1. Select the registration matching the downstream capability.
2. Obtain a service token with subject `campaign-service` and audience
   `flash-sale-internal-api`.
3. Attach `Authorization: Bearer <token>` only to the addressed internal endpoint.
4. Never forward the initiating administrator token.
5. Require Product/Inventory to revalidate issuer, signature, expiry, audience, subject, and scope.

## 7. Tracing and logging

Propagate W3C trace context through Micrometer Observation/OpenTelemetry instrumentation. Preserve
`X-Trace-Id` while it remains part of the approved repository HTTP contracts. Do not generate an
independent trace identity inside each Feign adapter.

Log at the outbound boundary with safe fields such as downstream service, operation, sanitized
status/error code, duration, and trace identity. Do not log:

- Authorization headers or access tokens;
- client IDs with secrets;
- cookies;
- full request/response bodies containing personal, payment, or security data;
- raw Feign exception bodies without sanitization.

## 8. Error and retry rules

Never expose `FeignException` outside the outbound adapter. Decode the downstream HTTP status and
stable machine-readable error code into an adapter-local failure, then translate it to an
application-owned result/exception.

Feature 017 examples:

| Downstream outcome | Campaign mapping |
|---|---|
| Product 404 `PRODUCT_VARIANT_NOT_FOUND` | 404 `PRODUCT_VARIANT_NOT_FOUND` |
| Product 409 `PRODUCT_VARIANT_NOT_SELLABLE` | 409 `PRODUCT_VARIANT_NOT_SELLABLE` |
| Inventory 409 `INVENTORY_INSUFFICIENT_STOCK` | 409 `INVENTORY_INSUFFICIENT_STOCK` |
| Inventory 409 `INVENTORY_ALLOCATION_REQUEST_CONFLICT` | 409 `INVENTORY_ALLOCATION_CONFLICT` |
| Downstream 401/403 | safe 503 plus secure log/metric; do not expose token detail |
| Timeout/connect/5xx | approved dependency-unavailable outcome |

General retry rules:

- Do not retry 400, 401, 403, 404, or deterministic business conflicts.
- Retry a query only when the plan defines attempts and backoff.
- Retry a command only when its contract defines idempotency and the exact request identity is
  reused.
- Treat an Inventory timeout as an ambiguous outcome; resume using the same durable
  `inventoryRequestId` rather than creating another allocation identity.
- Configure retry in one layer only to avoid multiplicative retries.

Prefer explicit no-retry behavior until a plan approves otherwise. Never return fabricated product,
price, stock, allocation, payment, or order data as a fallback.

## 9. Testing matrix

Application tests mock/fake output ports. Adapter tests use the existing approved HTTP-stub tooling
or add a test dependency through the plan before use.

Verify:

| Area | Assertions |
|---|---|
| Request | exact method, path, query, JSON, content type |
| Identity | correct service token; no administrator-token relay |
| Scope | Product and Inventory registrations cannot be substituted |
| Trace | `X-Trace-Id` and instrumented W3C context propagate |
| Success | remote DTO maps to caller-owned result; IDs and quantities match |
| Errors | stable 4xx codes, 401/403, 5xx, malformed body, timeout/connect failure |
| Idempotency | same request identity on replay; different payload conflict preserved |
| Architecture | domain/application do not import Feign or remote DTO packages |

Run module verification and any cross-service compatibility suite required by the feature plan.
Record exact commands and results in its evidence artifact.

## 10. Review anti-patterns

Reject:

- `@FeignClient` in application or domain;
- a shared root `common-feign-client` containing business contracts for several services;
- importing a downstream JPA entity/domain object as a response DTO;
- relaying an end-user/admin token to a background service call;
- broad OAuth scopes reused across unrelated endpoints;
- no timeout configuration;
- retrying every status or retrying non-idempotent writes;
- Feign calls within database transactions or item loops;
- dummy-data fallbacks;
- `FULL` production logging;
- Feign calls from the reactive API Gateway;
- using Feign for Kafka-style asynchronous work.
