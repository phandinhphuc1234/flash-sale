# Phase 22 Smoke Sequence Contract

All requests use the loopback Gateway base URL and a bounded timeout. Bearer values and passwords are
never included in evidence.

| Stage | Contract | Required assertion |
|---|---|---|
| Auth admin | `POST /api/v1/auth/login` | 200 and admin authorities |
| Auth shopper | `POST /api/v1/auth/register`, then `/login` | 201/200 and shopper subject |
| Product | `/api/v1/admin/catalog/products*` | draft, server-owned Variant composition, detail read-back, and ACTIVE publication |
| Inventory | Inventory-owned Job/CLI | one initialized item with positive quantity |
| Campaign | `/api/v1/admin/campaigns*` | create, item, schedule, activate |
| Reservation | `POST /api/v1/flash-sales/{campaignId}/reservations` | 202, then same-key identity replay |
| Reservation owner | `GET /api/v1/flash-sales/reservations/{reservationId}` | shopper ownership and matching IDs |
| Order | list `GET /api/v1/orders?page=0&size=100`, then detail `GET /api/v1/orders/{orderId}` | matching purchase/reservation/campaign/variant IDs and `PENDING_PAYMENT` |

The runner stops on the first failed assertion. A 202 without a matching Order before the bounded
deadline is a failed smoke, even when the reservation itself is durable.
