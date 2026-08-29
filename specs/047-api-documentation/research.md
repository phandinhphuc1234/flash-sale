# Research: Unified API Documentation

## Decision 1: Reuse the repository-approved Springdoc line

- **Decision**: Use Springdoc 2.8.17, already present in Inventory Service, and centralize its version
  in the root Maven POM.
- **Rationale**: This avoids introducing an unproven documentation stack and keeps all service
  documents on one version compatible with the existing Spring Boot line.
- **Alternatives considered**: static OpenAPI YAML for every service; a custom schema generator;
  Inventory-only documentation.

## Decision 2: Service-owned specs, Gateway-owned discovery

- **Decision**: Each service generates `/v3/api-docs`; Gateway exposes one local Swagger UI and
  exact proxy routes to those documents.
- **Rationale**: Request/response types and validation stay in the owning service while developers
  receive one entry point.
- **Alternatives considered**: one hand-merged spec at Gateway; direct service UIs only; public cloud
  documentation.

## Decision 3: Explicit opt-in and defense at Gateway

- **Decision**: Bind documentation generation to `API_DOCS_ENABLED=false` and make Gateway deny all
  documentation paths unless that flag is true.
- **Rationale**: Explicit Gateway denial prevents accidental exposure if one backend is
  misconfigured.
- **Alternatives considered**: always-on docs; JWT-admin-only public docs.

## Decision 4: Keep a reviewed human catalog

- **Decision**: Maintain a 40-row reader-facing catalog in addition to generated schemas.
- **Rationale**: Generated OpenAPI does not classify Gateway-public versus internal paths and does
  not discover the supported framework-owned OAuth token endpoint from controller mappings.
- **Alternatives considered**: generated docs only; code-search instructions only.

## Decision 5: Exclude unsupported framework routes

- **Decision**: Count `POST /oauth2/token` because it is an approved client-credentials contract.
  Exclude other authorization-server routes, `/error`, Actuator, and Swagger endpoints from the
  supported application count.
- **Rationale**: Totals should describe intentionally supported contracts, not every handler a
  framework may register.
- **Alternatives considered**: count every runtime handler; ignore the OAuth token endpoint.

## Data-model impact

None. This feature adds no entity, state transition, table, Redis key, or durable data. Therefore a
`data-model.md` artifact is intentionally omitted.
