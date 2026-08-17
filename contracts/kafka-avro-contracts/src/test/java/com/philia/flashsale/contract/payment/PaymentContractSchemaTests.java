package com.philia.flashsale.contract.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1;
import com.philia.flashsale.contract.payment.event.v1.PaymentFailedV1;
import com.philia.flashsale.contract.payment.event.v1.PaymentSucceededV1;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;

/** Contract-first guards for the Order-owned Payment command and facts. */
class PaymentContractSchemaTests {

    private static final Set<String> FORBIDDEN_TERMS = Set.of(
            "authorization", "card", "customer", "jwt", "password", "raw", "retry", "secret", "url");

    @Test
    void generatesTheApprovedSpecificRecordIdentities() {
        assertSpecificRecord(PaymentRequestedV1.class, "PaymentRequestedV1",
                "com.philia.flashsale.contract.payment.command.v1");
        assertSpecificRecord(PaymentSucceededV1.class, "PaymentSucceededV1",
                "com.philia.flashsale.contract.payment.event.v1");
        assertSpecificRecord(PaymentFailedV1.class, "PaymentFailedV1",
                "com.philia.flashsale.contract.payment.event.v1");
    }

    @Test
    void preservesEnvelopeLogicalTypesAndExactPaymentRequestedShape() throws Exception {
        Schema schema = parse("topics/flashsale.payment.commands.v1/PaymentRequestedV1.avsc");
        assertEnvelope(schema, "PaymentRequestedV1", 11);
        Schema data = schema.getField("data").schema();
        assertEquals(5, data.getFields().size());
        assertLogicalType(schema.getField("eventId").schema(), "uuid");
        assertLogicalType(schema.getField("occurredAt").schema(), "timestamp-millis");
        assertLogicalType(data.getField("orderId").schema(), "uuid");
        assertLogicalType(data.getField("amount").schema(), "decimal");
        assertLogicalType(data.getField("paymentDeadline").schema(), "timestamp-millis");
        assertForbiddenTermsAbsent(schema);
    }

    @Test
    void preservesSucceededAndFailedOutcomeShapesAndNullableProviderIds() throws Exception {
        Schema succeeded = parse("topics/flashsale.payment.events.v1/PaymentSucceededV1.avsc");
        Schema failed = parse("topics/flashsale.payment.events.v1/PaymentFailedV1.avsc");
        assertEnvelope(succeeded, "PaymentSucceededV1", 11);
        assertEnvelope(failed, "PaymentFailedV1", 11);
        assertEquals(8, succeeded.getField("data").schema().getFields().size());
        assertEquals(8, failed.getField("data").schema().getFields().size());
        assertLogicalType(succeeded.getField("data").schema().getField("amount").schema(), "decimal");
        assertLogicalType(failed.getField("data").schema().getField("amount").schema(), "decimal");
        assertLogicalType(succeeded.getField("data").schema().getField("paidAt").schema(), "timestamp-millis");
        assertLogicalType(failed.getField("data").schema().getField("failedAt").schema(), "timestamp-millis");
        assertEquals(Schema.Type.UNION, succeeded.getField("data").schema()
                .getField("providerPaymentIntentId").schema().getType());
        assertEquals(Schema.Type.UNION, failed.getField("data").schema()
                .getField("providerSessionId").schema().getType());
        assertEquals(JsonProperties.NULL_VALUE, succeeded.getField("data").schema()
                .getField("providerPaymentIntentId").defaultVal());
        assertEquals(JsonProperties.NULL_VALUE, failed.getField("data").schema()
                .getField("providerSessionId").defaultVal());
        assertForbiddenTermsAbsent(succeeded);
        assertForbiddenTermsAbsent(failed);
    }

    @Test
    void provesBackwardCompatibilityForOptionalOutcomeFields() throws Exception {
        Schema writer = parse("topics/flashsale.payment.events.v1/PaymentSucceededV1.avsc");
        Schema reader = new Schema.Parser().parse("""
                {
                  "type":"record",
                  "name":"PaymentSucceededV1",
                  "namespace":"com.philia.flashsale.contract.payment.event.v1",
                  "fields":[
                    {"name":"eventId","type":{"type":"string","logicalType":"uuid"}},
                    {"name":"eventType","type":"string"},
                    {"name":"eventVersion","type":"int"},
                    {"name":"producer","type":"string"},
                    {"name":"aggregateType","type":"string"},
                    {"name":"aggregateId","type":{"type":"string","logicalType":"uuid"}},
                    {"name":"aggregateVersion","type":"long"},
                    {"name":"correlationId","type":{"type":"string","logicalType":"uuid"}},
                    {"name":"causationId","type":{"type":"string","logicalType":"uuid"}},
                    {"name":"occurredAt","type":{"type":"long","logicalType":"timestamp-millis"}},
                    {"name":"data","type":{"type":"record","name":"PaymentSucceededDataV1","fields":[
                      {"name":"paymentId","type":{"type":"string","logicalType":"uuid"}},
                      {"name":"orderId","type":{"type":"string","logicalType":"uuid"}},
                      {"name":"amount","type":{"type":"bytes","logicalType":"decimal","precision":19,"scale":4}},
                      {"name":"currency","type":"string"},
                      {"name":"paidAt","type":{"type":"long","logicalType":"timestamp-millis"}},
                      {"name":"provider","type":"string"},
                      {"name":"providerSessionId","type":"string"},
                      {"name":"providerPaymentIntentId","type":["null","string"],"default":null},
                      {"name":"providerReceiptVersion","type":["null","long"],"default":null}
                    ]}}
                  ]
                }
                """);
        assertEquals(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE,
                SchemaCompatibility.checkReaderWriterCompatibility(reader, writer).getType());
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

    private void assertEnvelope(Schema schema, String name, int fieldCount) {
        assertEquals(name, schema.getName());
        assertEquals(fieldCount, schema.getFields().size());
        assertEquals("uuid", schema.getField("eventId").schema().getLogicalType().getName());
        assertEquals("timestamp-millis", schema.getField("occurredAt").schema().getLogicalType().getName());
        assertNotNull(schema.getField("data"));
    }

    private void assertLogicalType(Schema schema, String expected) {
        Schema effective = schema.getType() == Schema.Type.UNION
                ? schema.getTypes().stream().filter(candidate -> candidate.getType() != Schema.Type.NULL)
                        .findFirst().orElseThrow()
                : schema;
        assertNotNull(effective.getLogicalType());
        assertEquals(expected, effective.getLogicalType().getName());
    }

    private void assertForbiddenTermsAbsent(Schema schema) {
        String normalized = schema.toString().toLowerCase(Locale.ROOT);
        FORBIDDEN_TERMS.forEach(term -> assertFalse(normalized.contains(term),
                "Payment contract must not contain forbidden term: " + term));
    }

    private Schema parse(String resource) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Payment schema: " + resource);
            }
            return new Schema.Parser().parse(stream);
        }
    }
}
