# Implementation Plan: Technology and Architecture Documentation

**Branch**: `003-technology-architecture-docs` | **Date**: 2026-07-14 | **Spec**: [spec.md](spec.md)

## Summary

Add docs that explain the technology stack by problem area and create an architecture diagram folder with the first Mermaid overview. The docs must be honest about status: some technologies are already in the Maven build, while others are constitution-level or future architecture decisions.

## Technical Context

**Language/Version**: Documentation only; repository runtime remains Java 21 and Spring Boot 3.x

**Primary Dependencies**: No new dependencies

**Storage**: N/A

**Testing**: Static document inspection

**Target Platform**: Markdown rendered in GitHub/Codex-compatible viewers

**Project Type**: Maven microservice monorepo documentation

## Constitution Check

*GATE result before design: PASS. Re-check after design: PASS.*

- **Specification traceability**: This plan maps directly to FR-001 through FR-007.
- **Service ownership**: No service implementation or schema changes.
- **Communication**: No API, route, Kafka topic, or gRPC contract changes.
- **Data and messaging**: PostgreSQL, Redis, Kafka, outbox, and idempotency remain documented future concerns only.
- **Root infrastructure ownership**: No `infra/` runtime asset changes.
- **Observability**: Existing Actuator/Prometheus behavior is documented; future OpenTelemetry/Loki/Tempo/Grafana direction is described without implementation.
- **Contracts and dependencies**: No production dependency or public contract is added.
- **Validation**: Markdown and path inspection are sufficient.

## Project Structure

```text
docs/
├── technology/
│   ├── README.md
│   └── technology-problem-map.md
└── architecture/
    └── diagrams/
        ├── README.md
        ├── system-overview.md
        └── system-overview.mmd
```

## Documentation Decisions

- Use `Current`, `Planned`, and `Deferred` status labels.
- Keep diagram files as text so they can be reviewed in pull requests.
- Do not include Promtail as a recommended new component; use Grafana Alloy or OpenTelemetry Collector as the future log collection direction.
- Treat gRPC as deferred because it is shown in the reference image but is not present in the repository dependencies or Constitution.
- Document Liquibase as planned for service-owned PostgreSQL migrations. Do not add the dependency until a service feature introduces a real schema.

## Complexity Tracking

No constitutional violation or architecture exception is required.
