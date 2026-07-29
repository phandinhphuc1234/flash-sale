# Product service package structure

`product-service` uses the hybrid package-by-feature profile. The refactor is organizational only;
it does not change Product behavior or contracts.

```text
com/philia/flashsale/product/
├── ProductServiceApplication.java
├── catalog/
│   ├── domain/                         # shopper-facing catalog read models
│   ├── application/
│   │   └── service/                    # catalog query orchestration
│   └── adapter/
│       ├── in/web/                     # public catalog controllers/DTOs/mappers
│       └── out/persistence/            # catalog read entities/repositories
├── catalogadmin/
│   ├── domain/                         # lifecycle, money, actor, aggregate rules
│   ├── application/                   # admin commands, ports, results, use cases
│   └── adapter/
│       ├── in/web/                     # privileged admin HTTP boundary
│       └── out/persistence/            # admin write model, audit, idempotency
└── configuration/                     # Spring wiring and JWT/security configuration
```

Dependency direction remains:

```text
catalog/adapter       -> catalog/application       -> catalog/domain
catalogadmin/adapter  -> catalogadmin/application  -> catalogadmin/domain
configuration         -> feature application + adapters
```

HTTP DTOs, application commands/results, domain models, and JPA entities remain separate. Do not
create a new global `dto`, `mapper`, `exception`, or `utils` package. Add new types under the
feature and boundary that owns the responsibility.

For pagination, both catalog HTTP boundaries use the generic `PageResponse<T>` and `PageMeta` from
`libs/common-web`. Product-specific item DTOs and error responses stay local because they describe
Product’s own contract. The existing `ApiResponse<T>` envelope is not applied to these endpoints;
adopting it later would be a versioned HTTP-contract decision, not an implementation detail.

The old root-level `adapter`, `application`, `domain`, and duplicate `config` scaffolds were
removed after their contents were moved. Git may display the move as deleted old paths plus added
new paths; this is expected and does not indicate a second implementation.
