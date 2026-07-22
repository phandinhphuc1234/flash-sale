# Feature Specification: Product Catalog Schema

**Feature Branch**: `[008-product-catalog-schema]`

**Created**: 2026-07-16

**Status**: Approved

**Input**: User description: "Initialize the product-service database and run its first schema migration using the reviewed design: keep currency with variant price, add the corrected category index, defer outbox, and enforce media integrity."

## User Scenarios & Testing

### User Story 1 - Initialize durable Product catalog storage (Priority: P1)

As a Product developer, I can initialize an empty Product-owned database with the minimum useful catalog structures so later Product features do not invent incompatible tables independently.

**Why this priority**: Product APIs, persistence adapters, and campaign preparation need a stable durable ownership baseline before they can be implemented.

**Independent Test**: Apply the migration to an empty Product-owned database and verify that exactly the approved Product business tables, relationships, and migration ledger are present without requiring any Product API or Java persistence model.

**Acceptance Scenarios**:

1. **Given** an empty Product-owned database, **When** the first migration runs, **Then** it creates catalog structures for categories, products, variants, product/category membership, and media.
2. **Given** the migrated database, **When** catalog records are inserted in dependency order, **Then** valid category, product, variant, membership, and media data can be stored.
3. **Given** this schema-only feature, **When** Product source and contracts are reviewed, **Then** no Product API, gateway route, JPA entity, repository, Kafka event, Redis integration, or outbox behavior has been introduced.

---

### User Story 2 - Reject inconsistent Product data (Priority: P2)

As a Product maintainer, I can rely on the database to reject catalog states that would make price, category ordering, or media ownership ambiguous.

**Why this priority**: Database constraints provide the last line of defense when future adapters, imports, and administrative tools write catalog data.

**Independent Test**: Execute focused valid and invalid inserts against a migrated verification database and confirm that every approved invariant accepts valid data and rejects inconsistent data.

**Acceptance Scenarios**:

1. **Given** a variant price, **When** it is stored, **Then** amount and currency remain on the same variant record, amount cannot be negative, and the initial supported currency is VND.
2. **Given** category membership, **When** category products are ordered, **Then** ordering is non-negative, at most one membership is primary for a product, and the category browsing index orders by category, sort position, and product identity.
3. **Given** Product media linked to a variant, **When** the media is stored, **Then** the variant must belong to the same product as the media.
4. **Given** optional media dimensions or file size, **When** a value is provided, **Then** it must be positive; media order must be non-negative and media/status values must be from approved sets.
5. **Given** a reference to a missing product, category, parent category, or variant, **When** the record is written, **Then** the database rejects it.

---

### User Story 3 - Operate the migration safely and repeatedly (Priority: P3)

As a developer, I can run the Product migration locally, inspect what was applied, and rerun it without duplicate objects or destructive database reset instructions.

**Why this priority**: The first real migration establishes the operational pattern all later Product schema changes will follow.

**Independent Test**: Run the migration against local PostgreSQL, verify its ledger and schema, run it a second time, and confirm the second run reports no pending Product changes.

**Acceptance Scenarios**:

1. **Given** local PostgreSQL with `product_db`, **When** Product starts with its datasource and migrations enabled, **Then** the approved changeset is applied before the application becomes ready.
2. **Given** an already migrated `product_db`, **When** Product starts again, **Then** no business table, index, or constraint is duplicated and the recorded changeset is not reapplied.
3. **Given** an existing PostgreSQL volume where `product_db` is absent, **When** the developer follows the guide, **Then** the database is created explicitly and non-destructively rather than by deleting the volume.
4. **Given** a migration failure, **When** it is diagnosed, **Then** the changeset remains transactional, its rollback order is documented, and the Product service does not report readiness with a partially applied schema.

### Edge Cases

- An existing database may contain a table with the same name but no matching migration history; the migration must fail rather than silently adopt or overwrite it.
- A variant media reference may be null for product-level media; when it is non-null, both product and variant ownership must match.
- Optional width, height, weight, barcode, published time, descriptions, and file size may be null; provided physical measurements must be positive.
- Duplicate product code, product slug, SKU, category code, category slug, or non-null barcode must be rejected.
- Hard deletion behavior must not allow a parent Product record to disappear while dependent catalog records remain; lifecycle changes are expected to use status values until a later deletion feature defines policy.
- Category nesting cycles cannot be fully prevented by a simple foreign key and remain an application/domain rule for the future category-management feature.
- The migration must not create stock quantity, flash-sale quota, campaign price, Cart state, order data, or cross-service foreign keys.

## Requirements

### Functional Requirements

- **FR-001**: `product-service` MUST own and version the first Product catalog migration under its service-local changelog.
- **FR-002**: The migration MUST target only the Product-owned logical database `product_db` and MUST NOT access or modify another service database.
- **FR-003**: The first migration MUST create exactly five Product business tables: categories, products, product variants, product/category memberships, and product media.
- **FR-004**: Categories MUST support a stable identifier, optional parent category, unique code and slug, display name and description, lifecycle status, non-negative ordering, timestamps, and an optimistic version value.
- **FR-005**: Products MUST support a stable identifier, unique code and slug, name, short and full descriptions, flexible catalog attributes, lifecycle status, optional publication time, timestamps, and an optimistic version value.
- **FR-006**: Product variants MUST belong to one Product and support a stable identifier, globally unique SKU, optional unique barcode, name, flexible variant attributes, base price, currency, lifecycle status, optional positive weight, non-negative ordering, timestamps, and an optimistic version value.
- **FR-007**: Variant base price amount and currency MUST be stored together on the Product variant; amount MUST be non-negative and the initial accepted currency MUST be `VND`.
- **FR-008**: Product/category membership MUST use the Product and Category identifiers as its identity, support a primary flag and non-negative order, and allow at most one primary category membership per Product.
- **FR-009**: Product/category membership MUST provide the browsing index ordered by `(category_id, sort_order, product_id)`.
- **FR-010**: No additional partial browsing index for primary memberships MUST be added until an approved query requires it.
- **FR-011**: Product media MUST belong to a Product, MAY optionally belong to a Product variant, and MUST support media type, URL, optional descriptive/file metadata, non-negative ordering, lifecycle status, and timestamps.
- **FR-012**: Product media width, height, and file size MUST be nullable but MUST be positive whenever provided.
- **FR-013**: Product media type MUST be limited to `IMAGE` or `VIDEO`; category, product, variant, and media lifecycle statuses MUST be limited to their documented values.
- **FR-014**: A composite database relationship MUST prevent media from referencing a variant owned by a different Product.
- **FR-015**: Foreign keys and delete rules MUST prevent orphaned catalog records; association cleanup MAY cascade only where it cannot transfer or erase another service's data ownership.
- **FR-016**: The migration MUST include explicit rollback statements in reverse dependency order and MUST execute transactionally.
- **FR-017**: Product runtime dependencies and configuration MUST support PostgreSQL datasource creation and Liquibase execution without adding JPA or generating schema from Hibernate.
- **FR-018**: Local Compose configuration MUST provide `product-service` with its Product-owned datasource, keep Liquibase disabled on normal application replicas, and support an explicit one-off Product migration process with Liquibase enabled while keeping other service shells unchanged.
- **FR-019**: The existing lightweight Product application-context test MUST remain independent of a running database; actual migration verification MUST run separately against real PostgreSQL.
- **FR-020**: Migration validation MUST prove initial application, constraint behavior, expected indexes and foreign keys, migration-ledger recording, and an idempotent second run.
- **FR-021**: This feature MUST NOT create an outbox table or Product integration event; outbox is deferred until an approved feature requires reliable event publication.
- **FR-022**: This feature MUST NOT create Product controllers, routes, DTOs, mappers, use cases, domain classes, JPA entities, repositories, Redis state, Kafka producers/consumers, or cross-service contracts.
- **FR-023**: Existing completed feature artifacts `001` through `007` MUST remain historical and MUST NOT be rewritten for this schema delta.

### Key Entities

- **Category**: A hierarchical catalog grouping identified independently of Products; carries display, lifecycle, and ordering metadata.
- **Product**: The catalog-level sellable concept and owner of variants and media; it is not inventory, a campaign snapshot, a Cart item, or an Order line.
- **Product Variant**: A concrete SKU belonging to one Product; keeps its Money value as amount plus currency and may carry option attributes such as size or color.
- **Product Category Membership**: The ordered many-to-many relationship between Products and Categories, including the Product's optional primary category designation.
- **Product Media**: An image or video belonging to a Product and optionally one of that Product's variants.

## Success Criteria

### Measurable Outcomes

- **SC-001**: A new Product database can be initialized from source control in one documented run with five business tables and one recorded Product changeset.
- **SC-002**: All approved valid sample records can be inserted, while every tested invalid Money, category order, media measurement, duplicate natural key, orphan reference, and cross-Product media/variant case is rejected.
- **SC-003**: The category browsing index exposes the exact key order category, sort position, and Product identity.
- **SC-004**: A second migration run completes with zero pending Product changes and without changing the count of Product business tables, indexes, constraints, or migration records.
- **SC-005**: Product module verification and the full monorepo verification complete successfully without requiring PostgreSQL for ordinary context tests.
- **SC-006**: Static inspection finds zero outbox tables, Product business Java classes, public routes, event contracts, Redis code, or cross-service database references introduced by this feature.

## Assumptions

- PostgreSQL 17 is the local validation target already selected by the repository's Compose baseline.
- Identifiers are globally unique values generated independently of database row ordering.
- VND is the only accepted currency for this first schema; multi-market pricing requires a later price-model feature rather than moving currency away from amount.
- Brand identity and brand-specific behavior are deferred; the first schema creates neither a Brand table nor a canonical Product brand field.
- Product and variant flexible attributes are object-shaped catalog metadata and not a substitute for future domain validation.
- Status changes, rather than hard deletion, are the expected initial lifecycle mechanism.
- No production or shared environment is being migrated by this request; the requested execution target is the repository's local PostgreSQL environment.

## Human Decisions Required

No unresolved decision blocks this initial migration. Product API behavior, JPA mappings, category-cycle validation, managed brands, multi-market prices, catalog search, deletion policy, integration events, and outbox processing remain deliberately deferred.

## Constitutional Constraints

- **Service ownership**: `product-service` exclusively owns the five business tables, datasource access, and changelog; no cross-service database access or shared persistence model is introduced.
- **External ingress**: No Product endpoint or API Gateway route changes.
- **API/event contracts**: No HTTP, OpenAPI, Kafka, or AsyncAPI business contract is added or changed.
- **Durable and hot-path data**: PostgreSQL `product_db` becomes durable Product catalog truth. No stock/quota ownership, Redis key, or Lua operation is introduced.
- **Messaging reliability**: No event is published, so outbox, consumer idempotency, retry, ordering, and reconciliation are deferred with explicit scope protection.
- **Root infrastructure ownership**: The business migration remains under `services/product-service`; root Compose may supply the service-owned datasource but does not own Product schema SQL.
- **Observability**: Existing declarative health/readiness/liveness/Prometheus behavior remains; readiness during actual startup depends on successful datasource and migration initialization. No manual registry or tracing code is added.
- **Verification**: Real PostgreSQL migration/integrity/idempotency checks, Product module verification, Compose rendering, and full Maven verification apply. HTTP contract, Kafka, Redis, load, and Kubernetes tests are omitted because their artifacts and behaviors do not change.
- **Architecture decisions**: No ADR is required because this implements the already established Product database ownership and Liquibase migration standard without changing a boundary, communication style, or durability model.
