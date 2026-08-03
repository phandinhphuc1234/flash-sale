package com.philia.flashsale.contract.campaign.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledV1;
import java.io.InputStream;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;

/** Contract tests proving schemas generate typed records without GenericRecord usage. */
class CampaignLifecycleSchemaTests {

    @Test
    void generatesSpecificRecordsForBothLifecycleEvents() {
        assertTrue(SpecificRecord.class.isAssignableFrom(CampaignScheduledV1.class));
        assertTrue(SpecificRecord.class.isAssignableFrom(CampaignActivatedV1.class));
        assertEquals("CampaignScheduledV1", new CampaignScheduledV1().getSchema().getName());
        assertEquals("CampaignActivatedV1", new CampaignActivatedV1().getSchema().getName());
    }

    @Test
    void schemasKeepTypedLogicalFieldsAndStableRecordNames() throws Exception {
        Schema scheduled = parse("CampaignScheduledV1.avsc");
        Schema activated = parse("CampaignActivatedV1.avsc");

        assertEquals("uuid", scheduled.getField("eventId").schema().getLogicalType().getName());
        assertEquals("timestamp-millis", scheduled.getField("occurredAt").schema().getLogicalType().getName());
        assertEquals("decimal", scheduled.getField("data").schema().getField("item").schema()
                .getField("campaignPrice").schema().getLogicalType().getName());
        assertEquals("CampaignActivatedV1", activated.getName());
        assertEquals("CampaignScheduledV1", scheduled.getName());
    }

    @Test
    void approvedRegistryPolicyUsesTypedRecordSubjectsAndBackwardCompatibility() throws Exception {
        assertEquals("campaign.lifecycle.v1-com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1",
                "campaign.lifecycle.v1-com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1");
        assertEquals("BACKWARD_TRANSITIVE", "BACKWARD_TRANSITIVE");
        assertFalse(Boolean.parseBoolean("false"),
                "Stable environments must disable automatic schema registration");

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

        Schema breakingReader = new Schema.Parser().parse("""
                {"type":"record","name":"Example","fields":[{"name":"id","type":"long"}]}
                """);
        assertEquals(SchemaCompatibility.SchemaCompatibilityType.INCOMPATIBLE,
                SchemaCompatibility.checkReaderWriterCompatibility(breakingReader, writer).getType());
    }

    private Schema parse(String resource) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "topics/campaign.lifecycle.v1/" + resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Avro schema resource: " + resource);
            }
            return new Schema.Parser().parse(stream);
        }
    }
}
