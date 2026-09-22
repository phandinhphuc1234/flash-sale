# Research: Inventory Admin List and Product Display Lookup

## Decision 1: Keep Product and Inventory as separate owners

**Decision**: Inventory returns quantities and variant IDs; Product returns product/variant labels in
a bounded admin batch query.

**Rationale**: The repository prohibits cross-service database access. Inventory already stores a SKU
snapshot but does not own product names. A batch query avoids synchronous Inventory→Product coupling
and prevents frontend N+1 calls.

**Alternatives considered**:

- Joining the two service databases: rejected because it violates service ownership.
- Inventory calling Product once per row: rejected because it creates N+1 latency and failure coupling.
- A new read-model projection: deferred because current admin scale does not justify event-driven
  duplication and eventual-consistency repair work.

## Decision 2: Use existing page/size pagination

**Decision**: Use zero-based `page` and bounded `size`, mapped to the existing `PageResponse`.

**Rationale**: Product admin and stock movement APIs already use this repository convention. It keeps
the frontend and shared envelope consistent and avoids introducing a second `offset`/`limit` metadata
shape.

**Alternatives considered**:

- Raw `offset`/`limit`: rejected for this feature because `PageMeta` already models page numbers and
  the existing APIs use page/size.
- Cursor pagination: deferred until inventory volume and page drift justify it.

## Decision 3: Stable server ordering

**Decision**: Order by `updatedAt DESC, variantId ASC`; do not accept arbitrary sort expressions.

**Rationale**: Inventory changes frequently, and a deterministic tie-breaker makes refreshes less
surprising while preventing unsafe sort-field passthrough.

## Decision 4: Partial display failure is non-blocking

**Decision**: Keep stock rows visible if Product lookup fails or returns an unknown variant.

**Rationale**: Stock quantities are the operationally authoritative data for this page. Product labels
are enrichment and can be retried without hiding actionable inventory state.

## Scale boundary and evolution path

The current implementation is browser-side API composition, not an `Inventory Service -> Product
Service` runtime dependency. QuickCart first reads one bounded Inventory page, then sends one Product
batch request for the returned variant IDs. Therefore a Product outage does not invalidate the
authoritative stock response; the UI keeps the rows and falls back to `skuSnapshot`.

This is deliberately sufficient for the current admin use case. It is not the seckill critical path:
reservation uses the variant ID and quantity and must not call Product merely to render a name.

If measured admin traffic makes the Product batch endpoint a bottleneck, evolve in this order:

1. Add bounded timeouts and a metadata cache at the composition boundary.
2. Add an event-driven Product metadata projection only after an approved versioned Product event
   contract, idempotent consumer, replay policy, and eventual-consistency semantics exist.
3. Introduce a dedicated catalog/query read model only when multiple clients need the same composed
   view or cache pressure justifies the extra operational component.

Do not add product names to Inventory's authoritative write model, and do not introduce a Product
projection solely because the flash-sale reservation path is high throughput.
