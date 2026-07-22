# Research: Maven Multi-Module Microservice Skeleton

## Spring Boot 3.x baseline

- **Decision**: Use Spring Boot `3.5.16` with Java 21.
- **Rationale**: The user selected Spring Boot 3.x. Version 3.5.16 is the final and newest OSS
  release in the 3.5 line, supports Java 21, and is compatible with the repository's Maven 3.9.15
  installation.
- **Alternatives considered**:
  - Spring Boot 4.1.0: rejected because the user explicitly changed the baseline to 3.x.
  - Older Spring Boot 3.x minors: rejected because they are older and do not improve support status.
- **Lifecycle note**: Spring Boot 3.5.16 is the final OSS 3.5.x release. Moving to a supported 4.x
  line should be planned separately when the user permits a major upgrade.
- **Sources**:
  - https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/
  - https://docs.spring.io/spring-boot/3.5/system-requirements.html

## Spring Cloud release train

- **Decision**: Import `org.springframework.cloud:spring-cloud-dependencies:2025.0.3` in the root
  dependency management section.
- **Rationale**: Spring Cloud 2025.0.x is the official train for Spring Boot 3.5.x. Version 2025.0.3
  is the final train release and includes a Spring Cloud Gateway security fix, making it preferable
  to the initially considered 2025.0.2.
- **Alternatives considered**:
  - Spring Cloud 2025.1.2: rejected because it targets Spring Boot 4.x.
  - Spring Cloud 2025.0.2: compatible, but superseded by 2025.0.3 and lacks the later Gateway fix.
- **Lifecycle note**: The 2025.0.x train has ended OSS support. This accepted limitation follows the
  user's Spring Boot 3.x constraint and should be removed by a future Boot 4.x upgrade.
- **Sources**:
  - https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions
  - https://spring.io/blog/2026/06/11/spring-cloud-2025-0-3-aka-northfields-has-been-released/

## Gateway dependency

- **Decision**: Use `org.springframework.cloud:spring-cloud-starter-gateway-server-webflux` only in
  `api-gateway`, without `spring-boot-starter-web`.
- **Rationale**: This is the Gateway 4.3 WebFlux starter name managed by the 2025.0.x BOM. It provides
  the reactive Netty gateway runtime without introducing servlet MVC.
- **Alternatives considered**:
  - `spring-cloud-starter-gateway`: rejected because the old coordinate is deprecated in this train.
  - Servlet MVC gateway or mixed WebFlux/MVC: rejected because it is outside the requested baseline.
- **Source**:
  - https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/starter.html

## Actuator health and Prometheus

- **Decision**: Add `spring-boot-starter-actuator` plus runtime-scoped
  `io.micrometer:micrometer-registry-prometheus` to every service. Expose `health`, `info`, and
  `prometheus`, and enable health probes declaratively in YAML. Do not create Java registry beans.
- **Rationale**: Spring Boot auto-configures each registry found on the runtime classpath. The
  Prometheus registry is required for `/actuator/prometheus`; health probes provide liveness and
  readiness groups in local and container environments.
- **Alternatives considered**:
  - Actuator alone: rejected because it does not provide a Prometheus scrape endpoint.
  - Manual `PrometheusMeterRegistry` configuration: rejected as unnecessary and explicitly out of
    scope.
  - `management.metrics.export.prometheus.enabled`: rejected because it is not the Boot 3.5 property
    path. The correct `management.prometheus.metrics.export.enabled` already defaults to `true` and
    need not be set.
- **Sources**:
  - https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html
  - https://docs.spring.io/spring-boot/3.5/reference/actuator/metrics.html
  - https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html

## Maven reactor and wrapper

- **Decision**: Use one root parent/aggregator POM and nine child JAR modules. Generate a root-only
  wrapper with `mvn wrapper:wrapper -Dtype=bin`, using the installed Maven version 3.9.15.
- **Rationale**: Maven's reactor collects and builds declared modules. Parent inheritance centralizes
  dependency and plugin management, while aggregation provides one root build. The official `bin`
  distribution creates `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, and a genuine
  Apache `maven-wrapper.jar`; this avoids the observed PowerShell 5 failure in the 3.3.4
  `only-script` launcher while preserving the no-fake-binary constraint.
- **Alternatives considered**:
  - Separate standalone builds or per-service wrappers: rejected because they break the single-reactor
    requirement.
  - Hand-written wrapper scripts or a placeholder JAR: rejected because they are not official wrapper
    output.
  - Default `only-script` distribution: rejected after its generated Windows launcher failed before
    Maven startup by indexing a null `DirectoryInfo.Target` value for the normal `.m2` directory.
- **Sources**:
  - https://maven.apache.org/guides/mini/guide-multiple-modules.html
  - https://maven.apache.org/tools/wrapper/index.html

## Testing and validation depth

- **Decision**: Create one `@SpringBootTest` context-load test per service; run all nine module checks,
  the full Maven reactor, dependency inspection, and sequential runtime smoke checks for operational
  endpoints.
- **Rationale**: Context tests are the smallest meaningful test for application skeletons. Runtime
  smoke checks prove the health/probe/Prometheus contract. Full and isolated module builds prove both
  reactor integrity and independent buildability.
- **Alternatives considered**:
  - Database integration, business contract, Kafka, Redis, Kubernetes, and load tests: omitted because
    the feature intentionally introduces none of those behaviors or manifests.
  - Skipping tests: rejected by the specification and constitution.
