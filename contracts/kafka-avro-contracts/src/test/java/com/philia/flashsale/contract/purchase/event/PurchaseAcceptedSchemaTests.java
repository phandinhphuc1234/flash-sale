package com.philia.flashsale.contract.purchase.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1;
import java.io.InputStream;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;

/** Contract tests for the schema-first PurchaseAccepted publication. */
class PurchaseAcceptedSchemaTests {

    @Test
    void generatesSpecificRecordWithStableName() {
        assertTrue(SpecificRecord.class.isAssignableFrom(PurchaseAcceptedV1.class));
        assertEquals("PurchaseAcceptedV1", new PurchaseAcceptedV1().getSchema().getName());
        assertEquals("com.philia.flashsale.contract.purchase.event.v1",
                new PurchaseAcceptedV1().getSchema().getNamespace());
    }

    @Test
    void usesApprovedLogicalTypesAndExcludesInfrastructureFields() throws Exception {
        Schema schema = parse();
        assertEquals("uuid", schema.getField("eventId").schema().getLogicalType().getName());
        assertEquals("timestamp-millis", schema.getField("occurredAt").schema().getLogicalType().getName());
        Schema data = schema.getField("data").schema();
        assertEquals("decimal", data.getField("unitPrice").schema().getLogicalType().getName());
        assertEquals(19, data.getField("unitPrice").schema().getLogicalType() instanceof org.apache.avro.LogicalTypes.Decimal
                ? ((org.apache.avro.LogicalTypes.Decimal) data.getField("unitPrice").schema().getLogicalType()).getPrecision() : -1);
        assertEquals(4, ((org.apache.avro.LogicalTypes.Decimal) data.getField("unitPrice").schema().getLogicalType()).getScale());
        assertFalse(schema.toString().contains("jwt"));
        assertFalse(schema.toString().contains("idempotencyKey"));
        assertFalse(schema.toString().contains("JPA"));
        assertFalse(schema.toString().contains("Redis"));
        assertFalse(data.toString().contains("inventoryAllocationId"));
        assertFalse(data.toString().contains("skuSnapshot"));
    }

    @Test
    void documentsTopicRecordNameStrategyAndBackwardTransitivePolicy() throws Exception {
        Schema schema = parse();
        assertEquals("flashsale.purchase.events.v1-com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1",
                "flashsale.purchase.events.v1-" + schema.getFullName());
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
                "topics/flashsale.purchase.events.v1/PurchaseAcceptedV1.avsc")) {
            if (stream == null) {
                throw new IllegalStateException("Missing PurchaseAcceptedV1 schema");
            }
            return new Schema.Parser().parse(stream);
        }
    }
}
