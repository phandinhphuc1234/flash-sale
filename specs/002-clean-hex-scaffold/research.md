# Research: Clean Hexagonal Service Scaffold

## Decision: Use package-level Clean/Hexagonal zones inside each service

**Rationale**: The current repository is a Maven monorepo with independent Spring Boot service modules. Package-level separation keeps each service simple while still making the dependency direction visible.

**Alternatives considered**:

- Maven module per layer: rejected because it increases build complexity before there is real business logic.
- Feature folders first: rejected for the initial scaffold because services are still empty and need a common architectural baseline.

## Decision: Preserve empty directories with marker files

**Rationale**: Empty directories are not reliably tracked in version control. Marker files let reviewers see the intended structure without adding placeholder Java classes.

**Alternatives considered**:

- `package-info.java`: rejected for now because it creates production Java sources before real packages need source-level documentation.
- Dummy interfaces/classes: rejected because the user explicitly asked not to create full classes.

## Decision: Document business-capability port naming before creating ports

**Rationale**: Creating concrete port interfaces too early tends to freeze wrong abstractions. The safer first step is to document the naming rule and create interfaces only when a use case needs them.

**Alternatives considered**:

- Pre-create common ports: rejected because ports must be driven by real use cases and service-owned language.
- Technology-shaped ports: rejected because they leak adapter choices into application boundaries.
