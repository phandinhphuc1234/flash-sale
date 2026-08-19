# PaymentRequested DLT runbook

`flashsale.payment.payment-requested.dlt.v1` is the command-specific recovery topic for
`PaymentRequested.v1` records that cannot be accepted safely.

The Payment consumer uses manual acknowledgement. A record is acknowledged only after the local
Payment/inbox transaction returns successfully. Malformed envelope/key/trace data and contradictory
Payment identities are classified as non-retryable and published to this DLT. Transient PostgreSQL
or application storage failures are retried with the configured `1s,3s,10s` backoff before the same
DLT recovery path is used.

Operators may inspect the Kafka record headers (`kafka_dlt-*`, `traceparent`, and `tracestate`) and
the safe event/key identifiers. The original payload must not be copied into logs or tickets. A
replay must use the original `PaymentRequested.v1` value and the original `orderId` key after the
underlying contract or data problem has been corrected. Replaying is idempotent: the Payment inbox
and Payment aggregate converge to the existing identity rather than creating a second Payment.

Because Payment uses `TopicRecordNameStrategy` with schema auto-registration disabled, the
provisioning script registers `PaymentRequestedV1` for both the command topic and the command DLT
topic. Run `infra/docker/schema-registry/register-payment-schemas.ps1` before enabling the consumer;
otherwise a poison record cannot be serialized safely into the DLT.

Example local inspection (the project owner supplies the broker environment):

```bash
docker compose -f infra/docker/compose.yml exec -T kafka \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 \
  --topic flashsale.payment.payment-requested.dlt.v1 \
  --from-beginning --max-messages 1
```

Do not paste secrets, raw webhook bodies, authorization headers, or card data into this runbook or
operator output.
