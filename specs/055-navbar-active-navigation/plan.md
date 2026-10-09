# Implementation Plan: Storefront Active Navigation

**Branch**: `055-navbar-active-navigation` | **Date**: 2026-10-09 | **Spec**: [spec.md](spec.md)

**Status**: Approved — owner approved the specification and requested full implementation on 2026-10-09

## Summary

Give the existing QuickCart storefront navigation a truthful, accessible current-destination state.
Extract route classification into a small pure helper, render Home/Shop desktop links and a compact
mobile destination row, treat Cart as its own selected destination, remove Flash Sale/Help links
that currently point to Home, and preserve the account menu and Cart-count behavior. Use only the
existing Next.js, React, and Tailwind stack.

## Technical Context

**Language/Version**: JavaScript, React 19, Next.js 15.1.6

**Primary Dependencies**: Existing `next/link`, `next/navigation`, React, Tailwind CSS 3.4; no new dependency

**Storage**: N/A

**Testing**: Node built-in test runner, Next production build, focused browser/responsive review

**Target Platform**: Modern desktop/mobile browsers served by the existing Next.js storefront

**Project Type**: Frontend web application inside the Flash Sale monorepo

**Performance Goals**: Route classification is synchronous and constant-size; no network request or layout-heavy animation

**Constraints**: Preserve orange/white/slate theme, reduced-motion support, keyboard focus, no API/backend changes, no seller-navbar edit

**Scale/Scope**: One pure helper, one storefront Navbar component, one focused test file, four viewport checks

## Constitution Check

*Pre-research and post-design: PASS.*

| Principle | Design check |
|---|---|
| Specification traceability | FR-001–FR-012 map to the route helper, Navbar states, tests, and browser review. |
| Service ownership | Frontend-only; no service, persistence, repository, or schema change. |
| Communication | No HTTP, Gateway, Kafka, discovery, or contract behavior changes. |
| Data and messaging | PostgreSQL, Redis, stock, Order, Payment, outbox, and idempotency are untouched. |
| Root infrastructure ownership | No Docker, Kubernetes, Helm, or monitoring change. |
| Observability | No service health, metrics, logs, or trace change. |
| Contracts and dependencies | UI behavior contract is documented; no production dependency is added. |
| Validation | Focused Node tests, Next production build, browser accessibility/responsive review, and `git diff --check`; backend/load/Kubernetes gates are not applicable. |

## Research decisions

See [research.md](research.md). The implementation uses a persistent active indicator, text weight,
and subtle tint; adds `aria-current="page"`; keeps focus styling distinct; uses prefix matching only
for approved route groups; and does not render false destinations.

## Route classification design

The pure helper returns one of `home`, `shop`, `cart`, or `null`:

- `/` -> `home` only;
- `/all-products` and descendants -> `shop`;
- `/product/<id>` and descendants -> `shop`;
- `/cart` and descendants -> `cart`;
- account, Order, Payment, Reservation, login, seller, and unknown routes -> `null`.

Query strings and fragments are not part of `usePathname()` and therefore cannot change selection.
Prefix matching must use a full route-segment boundary so `/productivity` is not classified as
`/product`.

## Visual design

- Desktop Home/Shop links use a rounded hit target.
- Selected links use orange text, a very light orange tint, semibold weight, and a short orange
  underline/indicator centered under the label.
- Unselected hover uses a lighter tint and animated indicator growth.
- `focus-visible` uses a clear orange ring independent of selection.
- Cart uses the same selected vocabulary without changing its icon or count badge.
- Mobile retains the brand, Cart, and account row, then adds a compact Home/Shop row below it.
- Transitions are disabled for users who prefer reduced motion.

## Project Structure

### Documentation

```text
specs/055-navbar-active-navigation/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── storefront-navigation.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code

```text
flash-sale frontend/QuickCart/
├── components/
│   └── Navbar.jsx
├── lib/
│   └── storefrontNavigation.mjs
└── tests/
    └── storefrontNavigation.test.mjs
```

**Structure Decision**: Keep route semantics in a framework-independent helper so they can be tested
without a browser or React renderer. Keep styling and rendering in the existing Navbar component.

## Test strategy

1. Write route-classification tests before Navbar integration and observe the helper import/test fail.
2. Implement exact and segment-safe route matching.
3. Integrate the helper and selected-state accessibility into Navbar.
4. Run focused Node tests and existing frontend tests.
5. Run the production Next build.
6. Review 375, 768, 1024, and 1440 pixel widths and inspect `aria-current`/keyboard focus.
7. Run `git diff --check` and preserve unrelated dirty files.

## Complexity Tracking

No constitutional violation or architecture exception is required.
