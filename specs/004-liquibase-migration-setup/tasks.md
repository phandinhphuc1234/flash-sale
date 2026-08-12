---
description: "Tasks for Liquibase migration rulebook and service setup"
---

# Tasks: Liquibase Migration Setup

**Input**: Design documents from `specs/004-liquibase-migration-setup/`

## Phase 1: Spec Kit Artifacts

- [x] T001 Create feature specification in `specs/004-liquibase-migration-setup/spec.md`
- [x] T002 Create implementation plan in `specs/004-liquibase-migration-setup/plan.md`
- [x] T003 Create research, data model, contract, quickstart, checklist, and tasks artifacts under `specs/004-liquibase-migration-setup/`
- [x] T004 Update `.specify/feature.json` to point to `specs/004-liquibase-migration-setup`

## Phase 2: Migration Rulebook

- [x] T005 [P] [US1] Create Liquibase migration rules in `docs/technology/liquibase-migration-rules.md`
- [x] T006 [US1] Link the migration rules from `docs/technology/README.md` and `docs/technology/technology-problem-map.md`

## Phase 3: Service Dependency Setup

- [x] T007 [P] [US2] Add `org.liquibase:liquibase-core` to `services/authentication-service/pom.xml`
- [x] T008 [P] [US2] Add `org.liquibase:liquibase-core` to `services/product-service/pom.xml`
- [x] T009 [P] [US2] Add `org.liquibase:liquibase-core` to `services/campaign-service/pom.xml`
- [x] T010 [P] [US2] Add `org.liquibase:liquibase-core` to `services/flashsale-service/pom.xml`
- [x] T011 [P] [US2] Add `org.liquibase:liquibase-core` to `services/order-service/pom.xml`
- [x] T012 [P] [US2] Add `org.liquibase:liquibase-core` to `services/payment-service/pom.xml`
- [x] T013 [P] [US2] Add `org.liquibase:liquibase-core` to `services/notification-service/pom.xml`
- [x] T014 [P] [US2] Add `org.liquibase:liquibase-core` to `services/chatting-service/pom.xml`

## Phase 4: Service Changelog Setup

- [x] T015 [P] [US2] Add empty Liquibase changelog structure under `services/authentication-service/src/main/resources/db/changelog/`
- [x] T016 [P] [US2] Add empty Liquibase changelog structure under `services/product-service/src/main/resources/db/changelog/`
- [x] T017 [P] [US2] Add empty Liquibase changelog structure under `services/campaign-service/src/main/resources/db/changelog/`
- [x] T018 [P] [US2] Add empty Liquibase changelog structure under `services/flashsale-service/src/main/resources/db/changelog/`
- [x] T019 [P] [US2] Add empty Liquibase changelog structure under `services/order-service/src/main/resources/db/changelog/`
- [x] T020 [P] [US2] Add empty Liquibase changelog structure under `services/payment-service/src/main/resources/db/changelog/`
- [x] T021 [P] [US2] Add empty Liquibase changelog structure under `services/notification-service/src/main/resources/db/changelog/`
- [x] T022 [P] [US2] Add empty Liquibase changelog structure under `services/chatting-service/src/main/resources/db/changelog/`

## Phase 5: Service Configuration Setup

- [x] T023 [P] [US2] Configure Liquibase changelog path in `services/authentication-service/src/main/resources/application.yml`
- [x] T024 [P] [US2] Configure Liquibase changelog path in `services/product-service/src/main/resources/application.yml`
- [x] T025 [P] [US2] Configure Liquibase changelog path in `services/campaign-service/src/main/resources/application.yml`
- [x] T026 [P] [US2] Configure Liquibase changelog path in `services/flashsale-service/src/main/resources/application.yml`
- [x] T027 [P] [US2] Configure Liquibase changelog path in `services/order-service/src/main/resources/application.yml`
- [x] T028 [P] [US2] Configure Liquibase changelog path in `services/payment-service/src/main/resources/application.yml`
- [x] T029 [P] [US2] Configure Liquibase changelog path in `services/notification-service/src/main/resources/application.yml`
- [x] T030 [P] [US2] Configure Liquibase changelog path in `services/chatting-service/src/main/resources/application.yml`

## Phase 6: Validation

- [x] T031 Inspect `services/api-gateway/pom.xml` and confirm no Liquibase dependency exists
- [x] T032 Search service changelogs and confirm zero `changeSet` entries exist
- [x] T033 Run root verification with `.\mvnw.cmd clean verify`

## Execution Order

```text
Spec artifacts -> rulebook -> dependencies -> changelog structure -> application.yml -> validation
```
