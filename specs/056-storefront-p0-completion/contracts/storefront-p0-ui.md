# Storefront P0 UI Contract

## Routes

| Route | Purpose |
|---|---|
| `/flash-sale` | Live/upcoming public campaign discovery |
| `/flash-sale/[id]` | Campaign offer detail and reservation entry |
| `/help` | Shopping and account help |
| `/contact` | Honest support/contact information |
| `/privacy` | Portfolio privacy/data-handling notice |

## Global states

- `app/loading.jsx`: stable branded loading shell, no layout jump.
- `app/error.jsx`: sanitized message, Retry and Home actions, no raw exception text.
- `app/not-found.jsx`: branded 404 with Home and Shop actions.

## Campaign states

- Live + reservable: show authenticated reserve action.
- Signed out: action leads to login and preserves `returnTo`.
- Upcoming: show start time/countdown and disable reserve.
- Ended: show ended state and catalog navigation.
- Incomplete presentation: show campaign name and a neutral unavailable-detail label; never invent
  product name, price, or stock.
- Empty discovery: show a catalog action, not an error.
- Gateway failure/offline: show retry and safe navigation.

## Removed unsupported controls

- Newsletter subscription section.
- Wishlist heart buttons.
- Address-save page/control.
- Footer `#` placeholders.

## Accessibility/responsive

All new controls are keyboard reachable, have visible focus, honor reduced motion, and fit at 375,
768, 1024, and 1440 pixels without horizontal overflow.
