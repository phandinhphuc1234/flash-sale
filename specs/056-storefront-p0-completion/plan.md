# Implementation Plan: P0 Storefront Completion

**Branch**: `056-storefront-p0-completion` | **Date**: 2026-10-09 | **Spec**: `specs/056-storefront-p0-completion/spec.md`

**Status**: Approved — the project owner requested full P0 implementation on 2026-10-09.

## Summary

Complete the shopper-facing P0 experience by adding a bounded, anonymous Campaign read boundary,
exposing it through API Gateway, and building real Flash Sale discovery/detail pages that reuse the
existing authenticated reservation flow. Add global loading/error/not-found recovery, real Help,
Contact, and Privacy pages, and remove inactive Newsletter, Wishlist, and address controls.

Public campaign reads use Campaign-owned immutable scheduling snapshots. They do not call Product
Service or Inventory Service at read time, do not expose exact remaining quantity, and do not alter
the Redis reservation hot path.

## Technical Context

**Language/Version**: Java 21; JavaScript/React with Next.js 15.1.6

**Primary Dependencies**: Spring Boot 3, Spring MVC, Spring Data JPA, Spring Security, springdoc;
Next.js App Router, React, Tailwind CSS

**Storage**: Campaign-owned PostgreSQL tables already containing the public lifecycle and frozen
variant/price snapshot; no new table or migration

**Testing**: JUnit 5, Mockito, Spring MVC/security tests, Maven Surefire; Node test runner, ESLint,
Next.js production build

**Target Platform**: Docker/local development and Linux containers behind API Gateway/Kubernetes

**Project Type**: Maven microservice monorepo plus Next.js storefront

**Performance Goals**: Public list is paginated (default 12, maximum 50), performs one Campaign
database page query, and makes no synchronous downstream call

**Constraints**: Browser traffic uses Gateway only; no exact stock; no new reservation/payment
semantics; no new production dependency; public errors use safe repository envelopes and trace ID

**Scale/Scope**: One Campaign item per campaign, two public read endpoints, six shopper routes/states,
and existing reservation UI/API reuse

## Constitution Check

- **Specification traceability**: PASS. FR-001–FR-018 map to the contracts and tasks below; P1/P2
  remain out of scope.
- **Service ownership**: PASS. Campaign Service reads only Campaign PostgreSQL state. Product,
  Inventory, Flash Sale, Order, and Payment ownership remains unchanged.
- **Communication**: PASS. Browser ingress is API Gateway. Public campaign reads add no service call;
  reservation continues through the existing Gateway route.
- **Data and messaging**: PASS. PostgreSQL remains durable Campaign truth; Redis Lua and Kafka flows
  are untouched.
- **Root infrastructure ownership**: PASS. No shared deployment asset is required.
- **Observability**: PASS. Existing trace filter, Actuator, and Prometheus configuration remain in
  use; no registry is constructed in business code.
- **Contracts and dependencies**: PASS. New public HTTP contracts are documented before code; no
  dependency is added.
- **Validation**: PASS. Campaign/Gateway module tests and frontend test/lint/build are required;
  reservation regression remains covered without changing its contract.

## Project Structure

### Documentation

```text
specs/056-storefront-p0-completion/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── public-campaign-http.md
│   └── storefront-p0-ui.md
├── tasks.md
└── validation.md
```

### Source Code

```text
services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/
├── adapter/in/web/publicapi/
├── adapter/out/persistence/jpa/
└── application/{port,query,result,usecase}/

services/api-gateway/src/main/
├── java/com/philia/flashsale/gateway/security/
└── resources/application.yml

flash-sale frontend/QuickCart/
├── app/{flash-sale,help,contact,privacy}/
├── app/{loading,error,not-found}.jsx
├── components/
├── lib/
└── tests/
```

**Structure Decision**: Add the public Campaign adapter inside the existing Campaign feature and
keep HTTP DTOs out of application/domain packages. Keep browser data access in QuickCart `lib/api.js`
and UI policy in small testable helpers. No cross-service persistence or shared domain type is added.

## Design Decisions

1. **Campaign-owned snapshot composition**: Use frozen campaign name, SKU, base price, campaign
   price, timestamps, and per-user limit. This avoids Product fan-out and preserves historical offer
   truth. The response explicitly reports whether the snapshot is complete.
2. **Public lifecycle derivation**: Stored status is combined with authoritative timestamps at read
   time. Discovery excludes ended/draft records; detail can truthfully show an ended campaign.
3. **Reservation reuse**: Detail calls the existing authenticated
   `POST /api/v1/flash-sales/{campaignId}/reservations`; no new checkout shortcut is introduced.
4. **Safe UI boundaries**: Global App Router loading/error/not-found files sanitize user output and
   offer deterministic recovery routes.
5. **Honest navigation**: Newsletter, Wishlist, and address-save controls are removed. Static trust
   pages replace Footer placeholders.

## Complexity Tracking

No constitutional violation or ADR-triggering boundary change is introduced.
