# Tasks: Storefront Active Navigation

**Input**: Design documents from `specs/055-navbar-active-navigation/`

**Status**: Approved — owner approved the specification and requested full implementation on 2026-10-09

**Tests**: Route classification uses test-first development. Frontend build and browser review are
required completion gates.

## Phase 1: Setup and behavior contract

**Purpose**: Lock the bounded route/visual behavior before source changes.

- [x] T001 Verify `specs/055-navbar-active-navigation/contracts/storefront-navigation.md`, active feature pointer, completed requirements checklist, and unrelated dirty-file boundary before implementation.

---

## Phase 2: User Story 1 — Know the current storefront section (Priority: P1) 🎯 MVP

**Goal**: Home, Shop/product detail, and Cart expose one truthful persistent current destination.

**Independent Test**: Route tests classify all known/unknown/nested boundaries, and desktop browser
inspection finds one selected destination with `aria-current="page"` where applicable.

- [x] T002 [US1] Add route-family and segment-boundary tests in `flash-sale frontend/QuickCart/tests/storefrontNavigation.test.mjs` and record the expected pre-implementation failure.
- [x] T003 [US1] Implement the pure route classifier in `flash-sale frontend/QuickCart/lib/storefrontNavigation.mjs` until T002 passes.
- [x] T004 [US1] Integrate `usePathname`, accessible current-page state, selected/hover/focus/reduced-motion styling, truthful Home/Shop destinations, and selected Cart treatment in `flash-sale frontend/QuickCart/components/Navbar.jsx` without editing seller navigation.
- [x] T005 [US1] Run the focused and existing Node tests and record results in `specs/055-navbar-active-navigation/validation.md`.

**Checkpoint**: Desktop Home, Shop/product detail, and Cart navigation satisfy US1 independently.

---

## Phase 3: User Story 2 — Navigate clearly on a small screen (Priority: P2)

**Goal**: Small screens retain compact access to real destinations and a visible current state.

**Independent Test**: At 375 pixels, Home and Shop remain reachable, Cart stays readable, focus is
visible, labels do not wrap, and the page does not overflow horizontally.

- [x] T006 [US2] Add the compact mobile Home/Shop row and responsive selected state in `flash-sale frontend/QuickCart/components/Navbar.jsx` while preserving mobile Cart and Account controls.
- [x] T007 [US2] Review routes and responsive states at 375, 768, 1024, and 1440 pixels using `specs/055-navbar-active-navigation/quickstart.md`; record evidence or environment limitations in `specs/055-navbar-active-navigation/validation.md`.

**Checkpoint**: Mobile and desktop navigation both satisfy their independent acceptance scenarios.

---

## Phase 4: Cross-cutting validation and handoff

- [x] T008 Run `npm.cmd --prefix 'flash-sale frontend/QuickCart' run build`, all focused Node tests, and `git diff --check`; confirm no backend/API/dependency/seller-navigation change and finalize `specs/055-navbar-active-navigation/validation.md`.

## Dependencies and execution order

- T001 gates all production edits.
- T002 must run and fail before T003; T003 must pass before Navbar integration.
- T004 and T005 complete US1 before T006 begins because both stories edit the same Navbar file.
- T007 follows T006. T008 closes both stories.

## Implementation strategy

1. Lock the contract and dirty-file boundary.
2. Implement/test route semantics independently of React.
3. Deliver the desktop active state as the MVP.
4. Extend the same vocabulary to mobile.
5. Run production build and focused responsive/accessibility review.

No tasks are marked parallel because the implementation is intentionally small and the source tasks
share the same helper/Navbar/test dependency chain.
