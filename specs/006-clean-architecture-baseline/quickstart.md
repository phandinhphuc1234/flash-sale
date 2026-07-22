# Quickstart: Validate the Clean Architecture Working Baseline

Run these checks from the repository root after implementation.

## 1. Confirm the active feature

```powershell
Get-Content .specify/feature.json
```

Expected feature directory: `specs/006-clean-architecture-baseline`.

## 2. Confirm business-service query markers

The following eight services must contain an empty `application/query/.gitkeep` under their context package:

- authentication-service
- product-service
- campaign-service
- flashsale-service
- order-service
- payment-service
- notification-service
- chatting-service

`api-gateway` must not receive that marker.

## 3. Confirm the Java allowlist

```powershell
$java = Get-ChildItem services -Recurse -File -Filter *.java
$java.Count
$java.FullName
```

Expected: exactly 18 files—the nine existing `*Application.java` entry points and nine existing `*ApplicationTests.java` context tests.

## 4. Confirm Product behavior is still absent

```powershell
rg --hidden --line-number -g '*.java' -e 'ProductController|GetProduct|@GetMapping|@RestController|@Entity|JpaRepository' services
rg --hidden --line-number -i -g '*.yml' -g '*.yaml' -e '/api/(v[0-9]+/)?products?' -e 'Path=.*products?' services
```

Expected: no matches. `rg` returns exit code 1 when it finds no match; that is the passing result for these two checks.

## 5. Read the placement contract

Review [contracts/package-placement.md](contracts/package-placement.md) and [the repository architecture guide](../../docs/architecture/service-clean-hex-structure.md). Confirm the guide explains create-on-demand packages and boundary-local DTOs, mappers, errors, persistence types, and tests.

## 6. Run the cross-service regression build

```powershell
.\mvnw.cmd clean verify
```

Expected: every reactor module succeeds. Docker, database, Kafka, Redis, load, and Kubernetes checks do not apply because this feature changes none of those runtimes or contracts.

