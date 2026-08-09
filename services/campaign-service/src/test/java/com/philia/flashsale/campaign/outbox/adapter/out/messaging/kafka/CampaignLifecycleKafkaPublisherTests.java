package com.philia.flashsale.campaign.outbox.adapter.out.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.philia.flashsale.campaign.campaign.domain.event.CampaignActivated;
import com.philia.flashsale.campaign.campaign.domain.event.CampaignScheduled;
import com.philia.flashsale.campaign.configuration.CampaignKafkaProducerConfiguration;
import com.philia.flashsale.campaign.outbox.application.model.OutboxClaim;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledV1;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

/** Verifies T086's typed payload, stable key/header contract, and producer guarantees. */
class CampaignLifecycleKafkaPublisherTests {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final CampaignLifecycleAvroMapper mapper = new CampaignLifecycleAvroMapper(objectMapper);

    @Test
    void scheduledClaimBecomesSpecificRecordWithStableCampaignKeyAndW3cHeader() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        CampaignScheduled event = new CampaignScheduled(
                eventId, "CampaignScheduled", 1, "Campaign", campaignId, 1,
                Instant.parse("2030-08-01T10:00:00Z"),
                new CampaignScheduled.Data(
                        campaignId, "SUMMER-1", Instant.parse("2030-08-02T10:00:00Z"),
                        Instant.parse("2030-08-02T11:00:00Z"), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), "SKU-1", new BigDecimal("99.9900"), "VND", 10, 1));
        OutboxClaim claim = claim(eventId, campaignId, event);

        KafkaCampaignLifecyclePublisher publisher = new KafkaCampaignLifecyclePublisher(null, mapper);
        ProducerRecord<String, SpecificRecord> record = publisher.toProducerRecord(claim);

        assertThat(record.topic()).isEqualTo("campaign.lifecycle.v1");
        assertThat(record.key()).isEqualTo(campaignId.toString());
        assertThat(record.value()).isInstanceOf(CampaignScheduledV1.class);
        assertThat(header(record, "eventId")).isEqualTo(eventId.toString());
        assertThat(header(record, "eventType")).isEqualTo("CampaignScheduled");
        assertThat(header(record, "eventVersion")).isEqualTo("1");
        assertThat(header(record, "contentType")).isEqualTo("application/avro");
        assertThat(header(record, "traceparent")).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
    }

    @Test
    void activatedClaimUsesActivatedSpecificRecord() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        CampaignActivated event = new CampaignActivated(
                eventId, "CampaignActivated", 1, "Campaign", campaignId, 2,
                Instant.parse("2030-08-02T10:00:00Z"),
                new CampaignActivated.Data(
                        campaignId, "SUMMER-1", Instant.parse("2030-08-02T10:00:00Z"),
                        Instant.parse("2030-08-02T11:00:00Z"), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), "SKU-1", new BigDecimal("99.9900"), "VND", 10, 1));

        ProducerRecord<String, SpecificRecord> record = new KafkaCampaignLifecyclePublisher(null, mapper)
                .toProducerRecord(claim(eventId, campaignId, event));

        assertThat(record.value()).isInstanceOf(CampaignActivatedV1.class);
    }

    @Test
    void rejectsPayloadWhoseEventIdDiffersFromOutboxIdentity() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        CampaignActivated event = new CampaignActivated(
                eventId, "CampaignActivated", 1, "Campaign", campaignId, 2,
                Instant.parse("2030-08-02T10:00:00Z"),
                new CampaignActivated.Data(
                        campaignId, "SUMMER-1", Instant.parse("2030-08-02T10:00:00Z"),
                        Instant.parse("2030-08-02T11:00:00Z"), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), "SKU-1", new BigDecimal("99.9900"), "VND", 10, 1));

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRecord(claim(UUID.randomUUID(), campaignId, event)));
    }

    @Test
    void rejectsAnOutboxKeyThatIsNotTheCampaignId() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        CampaignActivated event = new CampaignActivated(
                eventId, "CampaignActivated", 1, "Campaign", campaignId, 2,
                Instant.parse("2030-08-02T10:00:00Z"),
                new CampaignActivated.Data(
                        campaignId, "SUMMER-1", Instant.parse("2030-08-02T10:00:00Z"),
                        Instant.parse("2030-08-02T11:00:00Z"), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), "SKU-1", new BigDecimal("99.9900"), "VND", 10, 1));
        OutboxClaim wrongKey = new OutboxClaim(
                eventId, campaignId, 1, "CampaignActivated", 1, "not-the-campaign-id",
                objectMapper.writeValueAsString(event), UUID.randomUUID().toString(),
                Instant.parse("2030-08-01T10:00:00Z"), Instant.parse("2030-08-01T10:01:00Z"));

        KafkaCampaignLifecyclePublisher publisher = new KafkaCampaignLifecyclePublisher(null, mapper);
        assertThrows(IllegalArgumentException.class, () -> publisher.toProducerRecord(wrongKey));
    }

    @Test
    void producerFactoryEnforcesApprovedAvroSafetySettings() {
        var properties = new CampaignKafkaProducerConfiguration().campaignProducerProperties(new KafkaProperties());

        assertThat(properties).containsEntry("acks", "all");
        assertThat(properties).containsEntry("enable.idempotence", true);
        assertThat(properties).containsEntry("auto.register.schemas", false);
        assertThat(properties.get("value.subject.name.strategy"))
                .isEqualTo("io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
    }

    private OutboxClaim claim(UUID eventId, UUID campaignId, Object event) throws Exception {
        return new OutboxClaim(
                eventId, campaignId, 1, event instanceof CampaignScheduled ? "CampaignScheduled" : "CampaignActivated",
                1, campaignId.toString(), objectMapper.writeValueAsString(event),
                UUID.randomUUID().toString(), Instant.parse("2030-08-01T10:00:00Z"),
                Instant.parse("2030-08-01T10:01:00Z"));
    }

    private String header(ProducerRecord<String, SpecificRecord> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
