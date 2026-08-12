# Architecture Diagrams

This folder stores source-controlled architecture diagrams.

## Files

- [system-overview.md](system-overview.md) renders the first overall architecture diagram in Markdown.
- [system-overview.mmd](system-overview.mmd) contains the raw Mermaid source for tools that render `.mmd` files.

## Diagram Rules

- Keep diagrams honest about implementation status.
- Use `Current` for repository behavior that exists now.
- Use `Planned` for architecture direction that needs future features.
- Use `Deferred` for ideas that need ADR approval before adoption.
- Prefer Mermaid text diagrams first; export PNG/SVG later only when a presentation needs it.

## Current Caveat

The reference image contains Promtail. Do not use Promtail in new diagrams or new infrastructure work. Promtail is EOL as of March 2, 2026, so future logging should use Grafana Alloy or OpenTelemetry Collector.
