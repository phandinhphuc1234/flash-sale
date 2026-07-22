# Quickstart: Validate Clean Hexagonal Scaffold

Use this guide after implementation to validate the scaffold without running a business workflow.

## 1. Inspect active feature

Expected active feature pointer:

```json
{
  "feature_directory": "specs/002-clean-hex-scaffold"
}
```

## 2. Inspect service package zones

Check that each service has visible architecture folders under its existing base package:

```text
domain/
application/
adapter/
configuration/
```

For core business services, also check:

```text
domain/model
domain/valueobject
domain/policy
domain/event
domain/exception
application/command
application/result
application/usecase
application/port/in
application/port/out
adapter/in/web
adapter/in/messaging
adapter/out/persistence
adapter/out/messaging
adapter/out/http
```

For `flashsale-service`, also check:

```text
adapter/out/redis
adapter/out/outbox
adapter/out/time
```

## 3. Confirm no implementation classes were added

The scaffold should add marker files and documentation only. Existing application entry points and context tests should remain the only Java classes unless later features add approved behavior.

## 4. Optional Maven verification

Because this feature adds non-Java marker files only, Maven behavior should be unchanged. The full confidence check is:

```powershell
.\mvnw.cmd clean verify
```

If the local shell or dependency resolution is unavailable, record the blocker and do not claim a passing build.
