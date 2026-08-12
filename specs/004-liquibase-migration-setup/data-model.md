# Data Model: Liquibase Migration Setup

This feature introduces no business data model.

## Setup Artifacts

- **Master Changelog**: `db.changelog-master.yaml`, the service-owned entry point for future includes.
- **Changes Folder**: `changes/`, the future home for service-owned changesets.
- **Migration Rulebook**: Repository documentation that defines how future migrations are authored, reviewed, validated, and rolled out.

## Explicit Non-Entities

- No table is introduced.
- No JPA entity is introduced.
- No repository is introduced.
- No seed data is introduced.
- No outbox table is introduced.
- No rollback script is introduced.
