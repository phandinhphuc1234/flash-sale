package com.philia.flashsale.contract.order.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.philia.flashsale.contract.order.event.v1.OrderCancelledV1;
import com.philia.flashsale.contract.order.event.v1.OrderConfirmedV1;
import com.philia.flashsale.contract.order.event.v1.OrderExpiredV1;
import com.philia.flashsale.contract.order.event.v1.OrderPaymentReviewRequiredV1;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;

/** Contract-first guards for terminal Order facts and the late-payment correction fact. */
class OrderLifecycleSchemaTests {

    private static final Set<String> FORBIDDEN_TERMS =
            Set.of("authorization", "card", "checkouturl", "jwt", "password", "secret", "webhook");

    @Test
    void generatesTheApprovedSpecificRecordIdentities() {
        assertSpecificRecord(OrderConfirmedV1.class, "OrderConfirmedV1");
        assertSpecificRecord(OrderCancelledV1.class, "OrderCancelledV1");
        assertSpecificRecord(OrderExpiredV1.class, "OrderExpiredV1");
        assertSpecificRecord(OrderPaymentReviewRequiredV1.class, "OrderPaymentReviewRequiredV1");
    }

    @Test
    void preservesConfirmedCancelledAndExpiredShapes() throws Exception {
        Schema confirmed = parse("OrderConfirmedV1.avsc");
        Schema cancelled = parse("OrderCancelledV1.avsc");
        Schema expired = parse("OrderExpiredV1.avsc");

        assertEnvelope(confirmed, "OrderConfirmed");
        assertEnvelope(cancelled, "OrderCancelled");
        assertEnvelope(expired, "OrderExpired");
        assertFieldNames(confirmed.getField("data").schema(),
                "orderId", "orderNumber", "purchaseRequestId", "reservationId", "paymentId", "confirmedAt");
        assertFieldNames(cancelled.getField("data").schema(),
                "orderId", "orderNumber", "purchaseRequestId", "reservationId", "reason", "cancelledAt");
        assertFieldNames(expired.getField("data").schema(),
                "orderId", "orderNumber", "purchaseRequestId", "reservationId", "reason", "expiredAt");
        assertLogicalType(confirmed.getField("data").schema().getField("confirmedAt").schema(),
                "timestamp-millis");
        assertLogicalType(cancelled.getField("data").schema().getField("cancelledAt").schema(),
                "timestamp-millis");
        assertLogicalType(expired.getField("data").schema().getField("expiredAt").schema(),
                "timestamp-millis");
        String cancelledReason = cancelled.getField("data").schema().getField("reason").doc();
        assertTrue(cancelledReason.contains("CHECKOUT_ATTEMPT_LIMIT_REACHED"));
        assertTrue(cancelledReason.contains("PROVIDER_TERMINAL_FAILURE"));
        assertEquals("PAYMENT_DEADLINE_EXPIRED", expired.getField("data").schema().getField("reason").doc());
    }

    @Test
    void preservesLatePaymentCorrectionShapeAndVocabulary() throws Exception {
        Schema review = parse("OrderPaymentReviewRequiredV1.avsc");

        assertEnvelope(review, "OrderPaymentReviewRequired");
        assertFieldNames(review.getField("data").schema(),
                "orderId", "orderNumber", "purchaseRequestId", "reservationId", "paymentId", "previousStatus",
                "reviewReason", "reviewRequiredAt");
        assertEquals("CANCELLED or EXPIRED", review.getField("data").schema().getField("previousStatus").doc());
        assertEquals("LATE_PAYMENT_RESERVATION_UNAVAILABLE",
                review.getField("data").schema().getField("reviewReason").doc());
        assertLogicalType(review.getField("data").schema().getField("reviewRequiredAt").schema(),
                "timestamp-millis");
    }

    @Test
    void documentsSubjectsCompatibilityAndSensitiveDataBoundary() throws Exception {
        for (String file : List.of(
                "OrderConfirmedV1.avsc",
                "OrderCancelledV1.avsc",
                "OrderExpiredV1.avsc",
                "OrderPaymentReviewRequiredV1.avsc")) {
            Schema schema = parse(file);
            assertEquals("flashsale.order.events.v1-com.philia.flashsale.contract.order.event.v1."
                            + file.replace(".avsc", ""),
                    "flashsale.order.events.v1-" + schema.getFullName());
            assertEquals(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE,
                    SchemaCompatibility.checkReaderWriterCompatibility(schema, schema).getType());
            assertForbiddenTermsAbsent(schema);
        }
    }

    private void assertSpecificRecord(Class<?> type, String name) {
        assertTrue(SpecificRecord.class.isAssignableFrom(type));
        try {
            Schema schema = (Schema) type.getMethod("getClassSchema").invoke(null);
            assertEquals(name, schema.getName());
            assertEquals("com.philia.flashsale.contract.order.event.v1", schema.getNamespace());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Generated SpecificRecord schema is unavailable", exception);
        }
    }

    private void assertEnvelope(Schema schema, String eventType) {
        assertEquals(11, schema.getFields().size());
        assertFieldNames(schema, "eventId", "eventType", "eventVersion", "producer", "aggregateType",
                "aggregateId", "aggregateVersion", "correlationId", "causationId", "occurredAt", "data");
        assertEquals(eventType, schema.getField("eventType").doc());
        assertEquals("1", schema.getField("eventVersion").doc());
        assertEquals("order-service", schema.getField("producer").doc());
        assertEquals("ORDER", schema.getField("aggregateType").doc());
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

    private void assertForbiddenTermsAbsent(Schema schema) {
        String normalized = schema.toString().toLowerCase(Locale.ROOT);
        FORBIDDEN_TERMS.forEach(term ->
                assertFalse(normalized.contains(term), "Order contract must not contain forbidden term: " + term));
    }

    private Schema parse(String file) throws Exception {
        String resource = "topics/flashsale.order.events.v1/" + file;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Order lifecycle schema: " + resource);
            }
            return new Schema.Parser().parse(stream);
        }
    }
}
