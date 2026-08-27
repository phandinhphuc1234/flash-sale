package com.philia.flashsale.contract.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.philia.flashsale.contract.purchase.command.v1.ConfirmPurchaseReservationV1;
import com.philia.flashsale.contract.purchase.command.v1.ReleasePurchaseReservationV1;
import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationConfirmedV1;
import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationReleasedV1;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;

/** Contract-first guards for reservation finalization commands and their durable results. */
class PurchaseReservationContractSchemaTests {

    private static final Set<String> FORBIDDEN_TERMS =
            Set.of("authorization", "card", "checkouturl", "jwt", "password", "secret", "webhook");

    @Test
    void generatesTheApprovedSpecificRecordIdentities() {
        assertSpecificRecord(ConfirmPurchaseReservationV1.class, "ConfirmPurchaseReservationV1",
                "com.philia.flashsale.contract.purchase.command.v1");
        assertSpecificRecord(ReleasePurchaseReservationV1.class, "ReleasePurchaseReservationV1",
                "com.philia.flashsale.contract.purchase.command.v1");
        assertSpecificRecord(PurchaseReservationConfirmedV1.class, "PurchaseReservationConfirmedV1",
                "com.philia.flashsale.contract.purchase.event.v1");
        assertSpecificRecord(PurchaseReservationReleasedV1.class, "PurchaseReservationReleasedV1",
                "com.philia.flashsale.contract.purchase.event.v1");
    }

    @Test
    void preservesExactCommandShapesAndLogicalTypes() throws Exception {
        Schema confirm = parse("flashsale.purchase.commands.v1", "ConfirmPurchaseReservationV1.avsc");
        Schema release = parse("flashsale.purchase.commands.v1", "ReleasePurchaseReservationV1.avsc");

        assertEnvelope(confirm, "ConfirmPurchaseReservation", "order-service", "PURCHASE_SAGA");
        assertEnvelope(release, "ReleasePurchaseReservation", "order-service", "PURCHASE_SAGA");
        assertFieldNames(confirm.getField("data").schema(),
                "sagaId", "orderId", "purchaseRequestId", "reservationId", "paymentId", "paidAt");
        assertFieldNames(release.getField("data").schema(),
                "sagaId", "orderId", "purchaseRequestId", "reservationId", "reason");
        assertLogicalType(confirm.getField("data").schema().getField("paidAt").schema(), "timestamp-millis");
        assertLogicalType(release.getField("data").schema().getField("orderId").schema(), "uuid");

        String reasonDoc = release.getField("data").schema().getField("reason").doc();
        assertTrue(reasonDoc.contains("PAYMENT_DEADLINE_EXPIRED"));
        assertTrue(reasonDoc.contains("CHECKOUT_ATTEMPT_LIMIT_REACHED"));
        assertTrue(reasonDoc.contains("PROVIDER_TERMINAL_FAILURE"));
        assertSubject("flashsale.purchase.commands.v1", confirm,
                "flashsale.purchase.commands.v1-"
                        + "com.philia.flashsale.contract.purchase.command.v1.ConfirmPurchaseReservationV1");
        assertSubject("flashsale.purchase.commands.v1", release,
                "flashsale.purchase.commands.v1-"
                        + "com.philia.flashsale.contract.purchase.command.v1.ReleasePurchaseReservationV1");
        assertKafkaKeyField(confirm, "orderId");
        assertKafkaKeyField(release, "orderId");
        assertForbiddenTermsAbsent(confirm);
        assertForbiddenTermsAbsent(release);
    }

    @Test
    void preservesExactResultShapesAndCurrentStateVocabulary() throws Exception {
        Schema confirmed = parse("flashsale.purchase.events.v1", "PurchaseReservationConfirmedV1.avsc");
        Schema released = parse("flashsale.purchase.events.v1", "PurchaseReservationReleasedV1.avsc");

        assertEnvelope(confirmed, "PurchaseReservationConfirmed", "flashsale-service", "PURCHASE_RESERVATION");
        assertEnvelope(released, "PurchaseReservationReleased", "flashsale-service", "PURCHASE_RESERVATION");
        assertFieldNames(confirmed.getField("data").schema(),
                "sagaId", "orderId", "purchaseRequestId", "reservationId", "paymentId", "confirmedAt");
        assertFieldNames(released.getField("data").schema(),
                "sagaId", "orderId", "purchaseRequestId", "reservationId", "reservationStatus", "reason",
                "releasedAt");
        assertLogicalType(confirmed.getField("data").schema().getField("confirmedAt").schema(),
                "timestamp-millis");
        assertLogicalType(released.getField("data").schema().getField("releasedAt").schema(),
                "timestamp-millis");
        assertEquals("RELEASED or EXPIRED", released.getField("data").schema().getField("reservationStatus").doc());
        assertSubject("flashsale.purchase.events.v1", confirmed,
                "flashsale.purchase.events.v1-"
                        + "com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationConfirmedV1");
        assertSubject("flashsale.purchase.events.v1", released,
                "flashsale.purchase.events.v1-"
                        + "com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationReleasedV1");
        assertKafkaKeyField(confirmed, "orderId");
        assertKafkaKeyField(released, "orderId");
        assertForbiddenTermsAbsent(confirmed);
        assertForbiddenTermsAbsent(released);
    }

    @Test
    void provesEveryNewRecordIsBackwardCompatibleWithItsInitialWriterSchema() throws Exception {
        for (Schema schema : List.of(
                parse("flashsale.purchase.commands.v1", "ConfirmPurchaseReservationV1.avsc"),
                parse("flashsale.purchase.commands.v1", "ReleasePurchaseReservationV1.avsc"),
                parse("flashsale.purchase.events.v1", "PurchaseReservationConfirmedV1.avsc"),
                parse("flashsale.purchase.events.v1", "PurchaseReservationReleasedV1.avsc"))) {
            assertEquals(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE,
                    SchemaCompatibility.checkReaderWriterCompatibility(schema, schema).getType());
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
        assertEquals(11, schema.getFields().size());
        assertFieldNames(schema, "eventId", "eventType", "eventVersion", "producer", "aggregateType",
                "aggregateId", "aggregateVersion", "correlationId", "causationId", "occurredAt", "data");
        assertEquals(eventType, schema.getField("eventType").doc());
        assertEquals("1", schema.getField("eventVersion").doc());
        assertEquals(producer, schema.getField("producer").doc());
        assertEquals(aggregateType, schema.getField("aggregateType").doc());
        assertLogicalType(schema.getField("eventId").schema(), "uuid");
        assertLogicalType(schema.getField("aggregateId").schema(), "uuid");
        assertLogicalType(schema.getField("correlationId").schema(), "uuid");
        assertLogicalType(schema.getField("causationId").schema(), "uuid");
        assertLogicalType(schema.getField("occurredAt").schema(), "timestamp-millis");
        assertNotNull(schema.getField("data"));
    }

    private void assertFieldNames(Schema schema, String... expected) {
        assertEquals(List.of(expected), schema.getFields().stream().map(Schema.Field::name).toList());
    }

    private void assertLogicalType(Schema schema, String expected) {
        assertNotNull(schema.getLogicalType());
        assertEquals(expected, schema.getLogicalType().getName());
    }

    private void assertSubject(String topic, Schema schema, String expected) {
        assertEquals(expected, topic + "-" + schema.getFullName());
    }

    private void assertKafkaKeyField(Schema schema, String expectedField) {
        Schema.Field field = schema.getField("data").schema().getField(expectedField);
        assertNotNull(field, "Approved Kafka key field must remain in the typed data record");
        assertLogicalType(field.schema(), "uuid");
    }

    private void assertForbiddenTermsAbsent(Schema schema) {
        String normalized = schema.toString().toLowerCase(Locale.ROOT);
        FORBIDDEN_TERMS.forEach(term ->
                assertFalse(normalized.contains(term), "Purchase contract must not contain forbidden term: " + term));
    }

    private Schema parse(String topic, String file) throws Exception {
        String resource = "topics/" + topic + "/" + file;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Purchase Saga schema: " + resource);
            }
            return new Schema.Parser().parse(stream);
        }
    }
}
