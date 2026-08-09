package com.philia.flashsale.campaign.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedDataV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledDataV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledItemV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledV1;
import com.philia.flashsale.campaign.outbox.adapter.out.messaging.kafka.W3CTraceContextHeaders;
import io.confluent.kafka.schemaregistry.CompatibilityChecker;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;

/** Contract-level tests for generated Avro records before a live Registry is contacted. */
class CampaignLifecycleEventContractTests {

    private static final Instant OCCURRED_AT = Instant.parse("2030-08-01T10:00:00.123Z");

    @Test
    void bothV1RecordsRoundTripAsSpecificRecordsWithStableEventIdentity() throws Exception {
        UUID scheduledId = UUID.randomUUID();
        UUID activatedId = UUID.randomUUID();

        CampaignScheduledV1 scheduled = scheduled(scheduledId);
        CampaignActivatedV1 activated = activated(activatedId);

        SpecificRecord scheduledDecoded = roundTrip(scheduled);
        SpecificRecord activatedDecoded = roundTrip(activated);

        assertThat(scheduledDecoded).isInstanceOf(CampaignScheduledV1.class);
        assertThat(activatedDecoded).isInstanceOf(CampaignActivatedV1.class);
        assertThat(((CampaignScheduledV1) scheduledDecoded).getEventId()).isEqualTo(scheduledId);
        assertThat(((CampaignActivatedV1) activatedDecoded).getEventId()).isEqualTo(activatedId);
        assertThat(((CampaignScheduledV1) scheduledDecoded).getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(((CampaignActivatedV1) activatedDecoded).getOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void approvedSchemasUseLogicalTypesAndContainNoCredentialMaterial() {
        Schema scheduled = CampaignScheduledV1.getClassSchema();
        Schema activated = CampaignActivatedV1.getClassSchema();

        assertThat(scheduled.getField("eventId").schema().getLogicalType().getName()).isEqualTo("uuid");
        assertThat(scheduled.getField("occurredAt").schema().getLogicalType().getName())
                .isEqualTo("timestamp-millis");
        Schema price = scheduled.getField("data").schema().getField("item").schema()
                .getField("campaignPrice").schema();
        assertThat(price.getLogicalType().getName()).isEqualTo("decimal");
        assertThat(activated.getField("eventId").schema().getLogicalType().getName()).isEqualTo("uuid");

        String schemas = (scheduled.toString() + activated).toLowerCase(java.util.Locale.ROOT);
        assertThat(schemas).doesNotContain("password", "secret", "token", "credential");
    }

    @Test
    void backwardTransitiveRegistryCheckAcceptsAnAdditiveDefaultedField() throws Exception {
        Schema original = CampaignScheduledV1.getClassSchema();
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) objectMapper.readTree(original.toString());
        ArrayNode fields = (ArrayNode) root.get("fields");
        ObjectNode additiveField = objectMapper.createObjectNode();
        additiveField.put("name", "operatorNote");
        additiveField.put("type", "string");
        additiveField.put("default", "");
        fields.add(additiveField);

        Schema revision = new Schema.Parser().parse(root.toString());
        assertThat(CompatibilityChecker.BACKWARD_TRANSITIVE_CHECKER.isCompatible(
                new AvroSchema(revision), List.of(new AvroSchema(original)))).isEmpty();
    }

    @Test
    void backwardTransitiveRegistryCheckRejectsARequiredFieldTypeChange() throws Exception {
        Schema original = CampaignActivatedV1.getClassSchema();
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) objectMapper.readTree(original.toString());
        for (JsonNode field : root.withArray("fields")) {
            if ("eventVersion".equals(field.path("name").asText())) {
                ((ObjectNode) field).put("type", "string");
            }
        }

        Schema breakingRevision = new Schema.Parser().parse(root.toString());
        assertThat(CompatibilityChecker.BACKWARD_TRANSITIVE_CHECKER.isCompatible(
                new AvroSchema(breakingRevision), List.of(new AvroSchema(original)))).isNotEmpty();
    }

    @Test
    void traceparentHeaderShapeIsW3cAndTraceIsNotAnAvroBusinessField() {
        String traceparent = new String(
                W3CTraceContextHeaders.traceparent("campaign-contract-trace"), StandardCharsets.UTF_8);

        assertThat(traceparent).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
        assertThat(CampaignScheduledV1.getClassSchema().getField("traceId")).isNull();
        assertThat(CampaignActivatedV1.getClassSchema().getField("traceId")).isNull();
    }

    private SpecificRecord roundTrip(SpecificRecord source) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
        new SpecificDatumWriter<SpecificRecord>(source.getSchema()).write(source, encoder);
        encoder.flush();
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(output.toByteArray(), null);
        return new SpecificDatumReader<SpecificRecord>(source.getSchema(), source.getSchema())
                .read(null, decoder);
    }

    private CampaignScheduledV1 scheduled(UUID eventId) {
        UUID campaignId = UUID.randomUUID();
        return new CampaignScheduledV1(
                eventId, "CampaignScheduled", 1, "Campaign", campaignId, 1L, OCCURRED_AT,
                new CampaignScheduledDataV1(
                        campaignId, "SUMMER-1", OCCURRED_AT, OCCURRED_AT.plusSeconds(3600),
                        new CampaignScheduledItemV1(
                                UUID.randomUUID(), UUID.randomUUID(), "SKU-1", new BigDecimal("99.9900"),
                                "VND", 10L, 1L)));
    }

    private CampaignActivatedV1 activated(UUID eventId) {
        UUID campaignId = UUID.randomUUID();
        return new CampaignActivatedV1(
                eventId, "CampaignActivated", 1, "Campaign", campaignId, 2L, OCCURRED_AT,
                new CampaignActivatedDataV1(campaignId, OCCURRED_AT, OCCURRED_AT.plusSeconds(3600)));
    }
}
