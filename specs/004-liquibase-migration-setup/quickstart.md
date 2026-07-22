# Quickstart: Validate Liquibase Setup

## 1. Verify active feature

```json
{
  "feature_directory": "specs/004-liquibase-migration-setup"
}
```

## 2. Verify Liquibase dependency placement

Expected:

- eight database-owning service POMs contain `org.liquibase:liquibase-core`
- `services/api-gateway/pom.xml` does not contain Liquibase

## 3. Verify changelog structure

Each database-owning service should contain:

```text
src/main/resources/db/changelog/db.changelog-master.yaml
src/main/resources/db/changelog/changes/.gitkeep
```

## 4. Verify no first migration exists

Search service changelogs for:

```text
changeSet
```

Expected result: zero matches.

## 5. Run verification

```powershell
.\mvnw.cmd clean verify
```

Expected result: reactor build succeeds and all existing service context tests pass.
