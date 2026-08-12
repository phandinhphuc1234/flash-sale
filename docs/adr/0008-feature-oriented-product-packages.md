# ADR 0008: Feature-oriented Product package layout

**Status**: Accepted  
**Date**: 2026-07-26  
**Scope**: `product-service` package organization only

## Context

`product-service` currently has a layer-first root containing catalog reads and catalog
administration writes. As both capabilities grow, the business language is hidden behind shared
`domain`, `application`, and `adapter` directories. The Authentication refactor established a
hybrid package-by-feature convention for this repository.

## Decision

Use two business feature roots while preserving Clean/Hexagonal boundaries inside each feature:

```text
product/
├── catalog/
│   ├── domain/
│   ├── application/
│   └── adapter/{in/web,out/persistence}
├── catalogadmin/
│   ├── domain/
│   ├── application/
│   └── adapter/{in/web,out/persistence}
├── configuration/
└── config/ technical classes are consolidated into configuration/
```

The move is mechanical only: move files, update package declarations/imports, remove obsolete empty
scaffolds, and preserve every method body, contract, migration, dependency, and test assertion.
The dependency direction remains `adapter -> application -> domain`; configuration may wire both.

## Alternatives considered

1. Keep the layer-first tree: no migration cost, but business capabilities remain difficult to find.
2. Put all classes directly under `catalog` or `catalogadmin`: easier to browse but weakens
   boundary visibility and encourages framework leakage.
3. Create a separate Product service for administration: unnecessary service-boundary change for
   a package-organization problem.

## Consequences

- Catalog query and administration flows are discoverable from the first package segment.
- Public and admin DTOs, use cases, persistence models, and exceptions remain separated by feature.
- The old root-level `adapter`, `application`, `domain`, and duplicate `config` scaffolds disappear
  once empty.
- Git will represent the move as deleted old paths plus added new paths; this is expected.

## Migration and validation

No production behavior is intentionally changed. Run Product module compilation/tests after the
move and record the result in Feature 010 tasks. If a method body or observable contract must
change, stop and create a separate approved feature task.
