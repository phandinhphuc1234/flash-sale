# Research: Storefront Active Navigation

## Decision 1 — Persistent active destination

**Decision**: Use an orange text/indicator treatment plus subtle background and font-weight change.

**Rationale**: A persistent active indicator makes the selected destination distinguishable from
hover. The project already uses orange for important shopper actions and slate for neutral text, so
this extends the existing visual language instead of introducing a new design vocabulary.

**Alternatives considered**:

- Color-only text: too easy to miss and relies on one visual cue.
- Large filled pill: visually competes with primary purchase actions.
- Animated sliding shared indicator: more state/layout complexity than this small navigation needs.

## Decision 2 — Accessible current page

**Decision**: Put `aria-current="page"` on the selected navigation link.

**Rationale**: W3C guidance uses `aria-current="page"` for the link representing the current page.
The attribute mirrors the visual state for assistive technology.

**Alternatives considered**:

- Visual styling only: inaccessible to users who cannot see the indicator.
- `aria-selected`: intended for selection widgets such as tabs/options, not ordinary site links.

## Decision 3 — Route families

**Decision**: Use explicit exact/prefix route families with segment boundaries.

**Rationale**: Home must match only `/`; product details belong to Shop; account-owned follow-up
pages must not inherit a false top-level destination. A pure helper makes this rule testable.

**Alternatives considered**:

- `pathname.startsWith('/')`: selects Home everywhere.
- Exact matching only: loses Shop context on product details.
- Page-owned active-state flags: duplicates route knowledge across screens.

## Decision 4 — False destinations

**Decision**: Remove Flash Sale and Help from primary navigation until real destinations exist.

**Rationale**: Both currently point to Home. Keeping them creates a false navigation promise and
conflicts with the approved scope that public Flash Sale discovery is not implemented.

**Alternatives considered**:

- Disabled “Coming soon” items: adds clutter and non-actionable controls.
- Keep routing to Home: preserves the current usability defect.
- Build new pages: outside this feature and requires separate requirements.

## Decision 5 — Mobile treatment

**Decision**: Add a compact second row for Home and Shop below the primary mobile header.

**Rationale**: The current desktop links disappear on small screens. A two-item row is visible,
stable, and avoids introducing a menu drawer solely for two destinations.

**Alternatives considered**:

- Hamburger drawer: extra state, focus management, and interaction cost for two links.
- Keep links hidden: does not satisfy mobile route awareness.
- Bottom navigation: larger global layout change and potential overlap with page content.

## Sources

- [W3C ARIA breadcrumb/current-page pattern](https://www.w3.org/WAI/ARIA/apg/patterns/breadcrumb/)
- [SAP Fiori navigation active indicator](https://www.sap.com/design-system/fiori-design-android/v26-4/components/navigation-and-search/navigation-bar/usage)
- [Material Design active/inactive navigation states](https://m2.material.io/develop/android/components/bottom-navigation/)
