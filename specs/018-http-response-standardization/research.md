# Research: HTTP Response Standardization

## Decision 1: Reuse `libs/common-web`

- **Decision**: Use existing `ApiResponse`, `ApiErrorResponse`, `FieldViolation`, `PageResponse`, and `PageMeta`.
- **Rationale**: The types already exist, are dependency-light, and preserve service ownership of business codes.
- **Alternatives considered**: Add `ResponseMeta`/`ApiErrorDetail` from the source draft; rejected because it would create a second public contract and conflict with the current `X-Trace-Id` convention.

## Decision 2: Keep service-owned error codes

- **Decision**: Shared envelopes only; Authentication, Product, Campaign, and Inventory retain their own codes.
- **Rationale**: A shared error-code enum would couple bounded contexts.
- **Alternatives considered**: One global error enum; rejected by service/data ownership rules.

## Decision 3: Separate Servlet and WebFlux handling

- **Decision**: MVC services use `@RestControllerAdvice` plus security handlers; Gateway uses reactive handlers/writer.
- **Rationale**: Security filters execute before MVC advice and Gateway is WebFlux.
- **Alternatives considered**: One shared Spring handler implementation; rejected because it would couple servlet and reactive stacks.

## Decision 4: Preserve behavior during migration

- **Decision**: Migrate one endpoint group at a time and update contract tests before removing local wrappers.
- **Rationale**: Existing Auth/Campaign/Product/Gateway bodies differ and shape changes are observable.
- **Alternatives considered**: Big-bang replacement; rejected because it obscures compatibility failures.
