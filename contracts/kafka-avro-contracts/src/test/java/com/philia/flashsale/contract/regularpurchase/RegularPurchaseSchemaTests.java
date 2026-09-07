package com.philia.flashsale.contract.regularpurchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.philia.flashsale.contract.cart.command.v1.ReconcilePurchasedCartSnapshotV1;
import com.philia.flashsale.contract.order.event.v2.OrderCancelledV2;
import com.philia.flashsale.contract.order.event.v2.OrderConfirmedV2;
import com.philia.flashsale.contract.order.event.v2.OrderCreatedV2;
import com.philia.flashsale.contract.order.event.v2.OrderExpiredV2;
import com.philia.flashsale.contract.regularhold.command.v1.ConfirmRegularStockHoldV1;
import com.philia.flashsale.contract.regularhold.command.v1.ReleaseRegularStockHoldV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldConfirmedV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldExpiredV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldReleasedV1;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;

/** Contract-first guards for additive regular-purchase Kafka records. */
class RegularPurchaseSchemaTests {

    private static final Set<String> FORBIDDEN_TERMS =
            Set.of("authorization", "card", "checkouturl", "jwt", "password", "secret", "webhook");

    @Test
    void generatesTheApprovedSpecificRecordIdentities() {
        assertSpecificRecord(ConfirmRegularStockHoldV1.class, "ConfirmRegularStockHoldV1",
                "com.philia.flashsale.contract.regularhold.command.v1");
        assertSpecificRecord(ReleaseRegularStockHoldV1.class, "ReleaseRegularStockHoldV1",
                "com.philia.flashsale.contract.regularhold.command.v1");
        assertSpecificRecord(RegularStockHoldConfirmedV1.class, "RegularStockHoldConfirmedV1",
                "com.philia.flashsale.contract.regularhold.event.v1");
        assertSpecificRecord(RegularStockHoldReleasedV1.class, "RegularStockHoldReleasedV1",
                "com.philia.flashsale.contract.regularhold.event.v1");
        assertSpecificRecord(RegularStockHoldExpiredV1.class, "RegularStockHoldExpiredV1",
                "com.philia.flashsale.contract.regularhold.event.v1");
        assertSpecificRecord(ReconcilePurchasedCartSnapshotV1.class, "ReconcilePurchasedCartSnapshotV1",
                "com.philia.flashsale.contract.cart.command.v1");
        assertSpecificRecord(OrderCreatedV2.class, "OrderCreatedV2",
                "com.philia.flashsale.contract.order.event.v2");
        assertSpecificRecord(OrderConfirmedV2.class, "OrderConfirmedV2",
                "com.philia.flashsale.contract.order.event.v2");
        assertSpecificRecord(OrderCancelledV2.class, "OrderCancelledV2",
                "com.philia.flashsale.contract.order.event.v2");
        assertSpecificRecord(OrderExpiredV2.class, "OrderExpiredV2",
                "com.philia.flashsale.contract.order.event.v2");
    }

    @Test
    void preservesRegularHoldCommandAndFactShapes() throws Exception {
        Schema confirm = parse("flashsale.inventory.regular-hold.commands.v1", "ConfirmRegularStockHoldV1.avsc");
        Schema release = parse("flashsale.inventory.regular-hold.commands.v1", "ReleaseRegularStockHoldV1.avsc");
        Schema confirmed = parse("flashsale.inventory.regular-hold.events.v1", "RegularStockHoldConfirmedV1.avsc");
        Schema released = parse("flashsale.inventory.regular-hold.events.v1", "RegularStockHoldReleasedV1.avsc");
        Schema expired = parse("flashsale.inventory.regular-hold.events.v1", "RegularStockHoldExpiredV1.avsc");

        assertEnvelope(confirm, "ConfirmRegularStockHold", "order-service", "PURCHASE_SAGA");
        assertEnvelope(release, "ReleaseRegularStockHold", "order-service", "PURCHASE_SAGA");
        assertEnvelope(confirmed, "RegularStockHoldConfirmed", "inventory-service", "REGULAR_STOCK_HOLD");
        assertEnvelope(released, "RegularStockHoldReleased", "inventory-service", "REGULAR_STOCK_HOLD");
        assertEnvelope(expired, "RegularStockHoldExpired", "inventory-service", "REGULAR_STOCK_HOLD");
        assertFieldNames(confirm.getField("data").schema(),
                "sagaId", "orderId", "purchaseRequestId", "holdId", "paymentId", "paidAt");
        assertFieldNames(release.getField("data").schema(),
                "sagaId", "orderId", "purchaseRequestId", "holdId", "paymentId", "reason", "desiredOrderStatus");
        assertFieldNames(confirmed.getField("data").schema(),
                "holdId", "purchaseRequestId", "orderId", "status", "items", "paymentId", "transitionedAt");
        assertFieldNames(released.getField("data").schema(),
                "holdId", "purchaseRequestId", "orderId", "status", "items", "reason", "transitionedAt");
        assertFieldNames(expired.getField("data").schema(),
                "holdId", "purchaseRequestId", "orderId", "status", "items", "transitionedAt");
        assertEquals("CANCELLED or EXPIRED", release.getField("data").schema()
                .getField("desiredOrderStatus").doc());
        assertEquals("CONFIRMED", confirmed.getField("data").schema().getField("status").doc());
        assertEquals("RELEASED", released.getField("data").schema().getField("status").doc());
        assertEquals("EXPIRED", expired.getField("data").schema().getField("status").doc());
        assertKafkaKeyField(confirm, "orderId");
        assertKafkaKeyField(release, "orderId");
        assertKafkaKeyField(confirmed, "orderId");
        assertKafkaKeyField(released, "orderId");
        assertKafkaKeyField(expired, "orderId");
    }

    @Test
    void preservesCartReconciliationAndAdditiveOrderV2Shapes() throws Exception {
        Schema reconciliation = parse("flashsale.cart.checkout.commands.v1", "ReconcilePurchasedCartSnapshotV1.avsc");
        Schema created = parse("flashsale.order.events.v1", "OrderCreatedV2.avsc");
        Schema confirmed = parse("flashsale.order.events.v1", "OrderConfirmedV2.avsc");
        Schema cancelled = parse("flashsale.order.events.v1", "OrderCancelledV2.avsc");
        Schema expired = parse("flashsale.order.events.v1", "OrderExpiredV2.avsc");

        assertEnvelope(reconciliation, "ReconcilePurchasedCartSnapshot", "order-service", "ORDER");
        assertFieldNames(reconciliation.getField("data").schema(), "orderId", "purchaseRequestId", "cartId",
                "ownerId", "snapshotCartVersion", "confirmedAt", "items");
        assertKafkaKeyField(reconciliation, "cartId");

        assertEnvelope(created, "OrderCreated", "order-service", "ORDER");
        assertFieldNames(created.getField("data").schema(), "orderId", "orderNumber", "purchaseRequestId",
                "userId", "purchaseSource", "stockParticipantType", "stockReferenceId", "cartId", "cartVersion",
                "status", "currency", "subtotalAmount", "totalAmount", "acceptedAt", "stockHoldExpiresAt",
                "paymentDeadline", "items");
        assertEquals("BUY_NOW or CART.", created.getField("data").schema().getField("purchaseSource").doc());
        assertEquals("REGULAR_STOCK_HOLD", created.getField("data").schema().getField("stockParticipantType").doc());
        assertEquals("PENDING_PAYMENT", created.getField("data").schema().getField("status").doc());
        assertLogicalType(created.getField("data").schema().getField("subtotalAmount").schema(), "decimal");
        assertLogicalType(created.getField("data").schema().getField("totalAmount").schema(), "decimal");

        for (Schema terminal : List.of(confirmed, cancelled, expired)) {
            assertEnvelope(terminal, terminal.getName().replace("V2", "").replace("Order", "Order"),
                    "order-service", "ORDER");
            assertKafkaKeyField(terminal, "orderId");
        }
        assertFieldNames(confirmed.getField("data").schema(), "orderId", "orderNumber", "purchaseRequestId",
                "purchaseSource", "stockParticipantType", "stockReferenceId", "paymentId", "confirmedAt");
        assertFieldNames(cancelled.getField("data").schema(), "orderId", "orderNumber", "purchaseRequestId",
                "purchaseSource", "stockParticipantType", "stockReferenceId", "reason", "cancelledAt");
        assertFieldNames(expired.getField("data").schema(), "orderId", "orderNumber", "purchaseRequestId",
                "purchaseSource", "stockParticipantType", "stockReferenceId", "reason", "expiredAt");
    }

    @Test
    void usesIndependentCompatibleTopicRecordNameSubjectsWithoutSensitiveData() throws Exception {
        for (Schema schema : List.of(
                parse("flashsale.inventory.regular-hold.commands.v1", "ConfirmRegularStockHoldV1.avsc"),
                parse("flashsale.inventory.regular-hold.commands.v1", "ReleaseRegularStockHoldV1.avsc"),
                parse("flashsale.inventory.regular-hold.events.v1", "RegularStockHoldConfirmedV1.avsc"),
                parse("flashsale.inventory.regular-hold.events.v1", "RegularStockHoldReleasedV1.avsc"),
                parse("flashsale.inventory.regular-hold.events.v1", "RegularStockHoldExpiredV1.avsc"),
                parse("flashsale.cart.checkout.commands.v1", "ReconcilePurchasedCartSnapshotV1.avsc"),
                parse("flashsale.order.events.v1", "OrderCreatedV2.avsc"),
                parse("flashsale.order.events.v1", "OrderConfirmedV2.avsc"),
                parse("flashsale.order.events.v1", "OrderCancelledV2.avsc"),
                parse("flashsale.order.events.v1", "OrderExpiredV2.avsc"))) {
            assertEquals(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE,
                    SchemaCompatibility.checkReaderWriterCompatibility(schema, schema).getType());
            assertForbiddenTermsAbsent(schema);
        }
    }

    private void assertSpecificRecord(Class<?> type, String name, String namespace) {
        assertTrue(SpecificRecord.class.isAssignableFrom(type));
        try {
            Schema schema = (Schema) type.getMethod("getClassSchema").invoke(null);
            assertEquals(name, schema.getName());
            assertEquals(namespace, schema.getNamespace());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Generated SpecificRecord schema is unavailable", exception);
        }
    }

    private void assertEnvelope(Schema schema, String eventType, String producer, String aggregateType) {
        assertEquals(13, schema.getFields().size());
        assertFieldNames(schema, "eventId", "eventType", "eventVersion", "producer", "aggregateType",
                "aggregateId", "aggregateVersion", "correlationId", "causationId", "occurredAt", "traceparent",
                "tracestate", "data");
        assertEquals(eventType, schema.getField("eventType").doc());
        assertEquals(producer, schema.getField("producer").doc());
        assertEquals(aggregateType, schema.getField("aggregateType").doc());
        assertLogicalType(schema.getField("eventId").schema(), "uuid");
        assertLogicalType(schema.getField("aggregateId").schema(), "uuid");
        assertLogicalType(schema.getField("correlationId").schema(), "uuid");
        assertLogicalType(schema.getField("causationId").schema(), "uuid");
        assertLogicalType(schema.getField("occurredAt").schema(), "timestamp-millis");
        assertTrue(schema.getField("traceparent").schema().isUnion());
        assertTrue(schema.getField("tracestate").schema().isUnion());
        assertNotNull(schema.getField("data"));
    }

    private void assertFieldNames(Schema schema, String... expected) {
        assertEquals(List.of(expected), schema.getFields().stream().map(Schema.Field::name).toList());
    }

    private void assertKafkaKeyField(Schema schema, String expectedField) {
        assertLogicalType(schema.getField("data").schema().getField(expectedField).schema(), "uuid");
    }

    private void assertLogicalType(Schema schema, String expected) {
        assertNotNull(schema.getLogicalType());
        assertEquals(expected, schema.getLogicalType().getName());
    }

    private void assertForbiddenTermsAbsent(Schema schema) {
        String normalized = schema.toString().toLowerCase(Locale.ROOT);
        FORBIDDEN_TERMS.forEach(term ->
                assertFalse(normalized.contains(term), "Contract must not contain forbidden term: " + term));
    }

    private Schema parse(String topic, String file) throws Exception {
        String resource = "topics/" + topic + "/" + file;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing regular purchase schema: " + resource);
            }
            return new Schema.Parser().parse(stream);
        }
    }
}
