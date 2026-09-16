# Operational Runbooks

Runbooks describe a bounded recovery procedure. Confirm the target environment, current Git
revision, and authoritative owner before running a mutating step.

- [`payment-service-recovery.md`](payment-service-recovery.md) — diagnose and recover Payment
  intake/provider/webhook/outbox state.
- [`regular-purchase-checkout.md`](regular-purchase-checkout.md) — diagnose Buy Now/Cart checkout,
  Inventory holds, Order Saga, and Cart reconciliation.

Capture logs and identifiers without exposing JWTs, provider secrets, Checkout URLs, webhook bodies,
or another shopper's data. A DLT record is operational evidence, not permission to invent a business
outcome.
