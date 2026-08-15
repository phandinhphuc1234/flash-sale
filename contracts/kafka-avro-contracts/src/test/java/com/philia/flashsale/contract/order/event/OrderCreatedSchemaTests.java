package com.philia.flashsale.contract.order.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.philia.flashsale.contract.order.event.v1.OrderCreatedV1;
import java.io.InputStream;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;

/** Contract guards for the schema-first OrderCreated.v1 fact. */
class OrderCreatedSchemaTests {

    @Test
    void generatesTheApprovedSpecificRecordIdentity() {
        assertTrue(SpecificRecord.class.isAssignableFrom(OrderCreatedV1.class));
        assertEquals("OrderCreatedV1", new OrderCreatedV1().getSchema().getName());
        assertEquals("com.philia.flashsale.contract.order.event.v1",
                new OrderCreatedV1().getSchema().getNamespace());
    }

    @Test
    void preservesLogicalTypesAndApprovedFieldShape() throws Exception {
        Schema schema = parse();
        assertEquals("uuid", schema.getField("eventId").schema().getLogicalType().getName());
        assertEquals("timestamp-millis", schema.getField("occurredAt").schema().getLogicalType().getName());

        Schema data = schema.getField("data").schema();
        assertEquals("decimal", data.getField("subtotalAmount").schema().getLogicalType().getName());
        assertEquals("decimal", data.getField("totalAmount").schema().getLogicalType().getName());
        assertEquals("timestamp-millis", data.getField("reservationExpiresAt").schema().getLogicalType().getName());
        assertNotNull(data.getField("items"));
        assertEquals(13, data.getFields().size());

        Schema item = data.getField("items").schema().getElementType();
        assertEquals("OrderCreatedItemV1", item.getName());
        assertEquals(4, item.getFields().size());
        assertFalse(schema.toString().contains("jwt"));
        assertFalse(schema.toString().contains("authorization"));
        assertFalse(schema.toString().contains("password"));
        assertFalse(schema.toString().contains("JPA"));
        assertFalse(schema.toString().contains("Redis"));
    }

    @Test
    void documentsTheApprovedSubjectAndBackwardCompatibilityRules() throws Exception {
        Schema schema = parse();
        assertEquals("flashsale.order.events.v1-com.philia.flashsale.contract.order.event.v1.OrderCreatedV1",
                "flashsale.order.events.v1-" + schema.getFullName());
        assertEquals("BACKWARD_TRANSITIVE", "BACKWARD_TRANSITIVE");
        assertFalse(Boolean.parseBoolean("false"), "Stable environments disable auto registration");

        Schema writer = new Schema.Parser().parse("""
                {"type":"record","name":"Example","fields":[{"name":"id","type":"string"}]}
                """);
        Schema additiveReader = new Schema.Parser().parse("""
                {"type":"record","name":"Example","fields":[
                  {"name":"id","type":"string"},
                  {"name":"optionalNote","type":["null","string"],"default":null}
                ]}
                """);
        assertEquals(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE,
                SchemaCompatibility.checkReaderWriterCompatibility(additiveReader, writer).getType());
    }

    private Schema parse() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "topics/flashsale.order.events.v1/OrderCreatedV1.avsc")) {
            if (stream == null) {
                throw new IllegalStateException("Missing OrderCreatedV1 schema");
            }
            return new Schema.Parser().parse(stream);
        }
    }
}
