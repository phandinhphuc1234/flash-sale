# Payment Service recovery runbook

This runbook is for the Payment Service alerts emitted by the Prometheus rules under
`infra/monitoring/prometheus/rules/payment-service-alerts.yml`. It deliberately uses only safe
operator actions: no card data, Stripe credentials, webhook signatures, Checkout URLs, raw bodies,
or authorization headers belong in tickets, logs, or chat.

## First checks

1. Open the Payment Service dashboard and inspect `/actuator/health/readiness` and
   `/actuator/prometheus` through the internal monitoring path.
2. Confirm PostgreSQL is reachable and the `paymentReadiness` component reports `UP` for the
   durable database. Kafka, Schema Registry, and Stripe may be degraded without making owner reads
   unavailable; record their status categories only.
3. Correlate the alert window with the service `traceId`, Payment aggregate ID, Order ID, or outbox
   event ID. Do not copy request headers or provider payloads into the incident.

## `PaymentManualReviewPresent`

Manual review means provider truth could not be established safely after bounded recovery. Do not
retry Checkout creation with a new idempotency key and do not mark the Payment successful or failed
by hand.

1. Record the safe Payment/Order identity and current aggregate status.
2. Check Stripe test/live mode and provider API availability using the provider dashboard without
   pasting credentials into the incident.
3. Inspect the recovery work category, attempt count, lease age, and the latest redacted outcome
   category.
4. Escalate to the project owner for the approved forward-recovery or business decision. Automatic
   refunds and direct database edits are out of scope for this MVP.

## `PaymentRecoveryWorkAgeHigh`

1. Check worker liveness and the `payment_recovery_queue_size` metric.
2. Verify the configured worker is enabled and that no stale lease blocks claiming.
3. Check PostgreSQL connection-pool saturation and provider timeout categories.
4. Restore the dependency or worker, then verify the queue age decreases and the aggregate version
   advances monotonically. Never delete recovery rows to clear the alert.

## `PaymentOutboxLagHigh`

1. Check Kafka broker and Schema Registry reachability and the outbox publisher liveness.
2. Inspect pending count, oldest-event age, retry category, and stable event IDs.
3. Restore broker/registry availability and allow the publisher to replay the same outbox identity.
4. Verify the event is acknowledged before it is marked published. Duplicate delivery is safe and
   must be handled by the downstream idempotent consumer.

## Closure evidence

Record alert start/end time, safe metric values, dependency status categories, and the validation
command or dashboard link. Redact secrets, signatures, access tokens, card/customer fields, raw
webhook JSON, provider URLs, and Checkout URLs before attaching evidence.
